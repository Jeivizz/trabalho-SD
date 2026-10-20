package org.example.pratica3;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoAnel {

    // Constantes do cenário pedido no PDF
    public static final int TOTAL_NOS = 6;
    public static final int ARQUIVOS_POR_NO = 10;
    public static final int TOTAL_ARQUIVOS = TOTAL_NOS * ARQUIVOS_POR_NO; // 60
    public static final int PORTA_BASE = 7000; // P0=7000, P1=7001, ...
    public static final String MSG_ERRO_ENTRADA = "Erro na entrada de dados. Tente outra vez!";

    // Regex que valida o formato "arquivoN.ext" (N de 1 a 9 dígitos).
    // Rejeita "arquivoABC.ext" e "arquivo0.ext" já na validação sintática.
    private static final Pattern FORMATO = Pattern.compile("^arquivo([1-9][0-9]{0,8})\\.ext$");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final int id;
    private final int sucessor;    // (id+1) % 6  -> vizinho no sentido horário
    private final int antecessor;  // (id+5) % 6  -> vizinho no sentido anti-horário
    private final Set<String> arquivos = new HashSet<>(); // arquivos locais deste nó

    // Correlaciona id da busca -> futuro que será completado quando a RESPOSTA chegar.
    // ConcurrentHashMap porque várias threads do pool acessam.
    private final Map<String, CompletableFuture<Mensagem>> pendentes = new ConcurrentHashMap<>();
    // Log de mensagens enviadas/recebidas (o PDF exige armazenar e imprimir)
    private final List<String> historico = Collections.synchronizedList(new ArrayList<>());
    // Pool de threads: cada conexão aceita é tratada em paralelo
    private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private ServerSocket servidor;

    public NoAnel(int id) {
        this.id = id;
        // Topologia em anel: sucessor e antecessor calculados com módulo
        this.sucessor = (id + 1) % TOTAL_NOS;
        this.antecessor = (id + TOTAL_NOS - 1) % TOTAL_NOS;
        // Distribuição pedida: P0 tem 1..10, P1 tem 11..20, ..., P5 tem 51..60
        for (int k = id * ARQUIVOS_POR_NO + 1; k <= (id + 1) * ARQUIVOS_POR_NO; k++) {
            arquivos.add("arquivo" + k + ".ext");
        }
    }

    public int getId() {
        return id;
    }

    // Sobe o ServerSocket na porta do nó e inicia a thread aceitadora
    public void iniciar() throws IOException {
        servidor = new ServerSocket(PORTA_BASE + id, 50, InetAddress.getLoopbackAddress());
        Thread aceitador = new Thread(this::aceitarConexoes, "P" + id + "-aceitador");
        aceitador.setDaemon(true);
        aceitador.start();
        log("No iniciado na porta " + (PORTA_BASE + id) + " | sucessor=P" + sucessor
                + " antecessor=P" + antecessor
                + " | arquivos " + (id * ARQUIVOS_POR_NO + 1) + " a " + ((id + 1) * ARQUIVOS_POR_NO));
    }

    public void parar() {
        try {
            if (servidor != null) {
                servidor.close(); // faz accept() lançar exceção e sair do loop
            }
        } catch (IOException ignorado) {
            // fechamento normal
        }
    }

    // Loop da thread aceitadora: cada conexão vira uma tarefa no pool
    private void aceitarConexoes() {
        while (!servidor.isClosed()) {
            try {
                Socket s = servidor.accept();
                pool.execute(() -> tratarConexao(s));
            } catch (IOException e) {
                if (!servidor.isClosed()) {
                    log("Erro ao aceitar conexao: " + e.getMessage());
                }
            }
        }
    }

    // Lê UMA mensagem da conexão e despacha conforme o tipo
    private void tratarConexao(Socket s) {
        try (Socket socket = s; ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            Mensagem m = (Mensagem) in.readObject(); // desserializa a mensagem composta
            if (m.tipo == Mensagem.Tipo.BUSCA) {
                processarBusca(m);
            } else {
                processarResposta(m);
            }
        } catch (Exception e) {
            // Mensagem inválida é descartada sem derrubar o nó (robustez)
            log("Mensagem invlida/erro de leitura descartado: " + e);
        }
    }

    // ------------------------------------------------------------------
    // Iniciar uma busca (chamado pelo console deste nó)
    // ------------------------------------------------------------------

    /**
     * Valida a entrada e, se válida, inicia a busca no anel.
     * Retorna null se a entrada for inválida (nada é enviado ao anel).
     */
    public CompletableFuture<Mensagem> buscar(String entrada) {
        String arq = entrada == null ? "" : entrada.trim();
        Matcher m = FORMATO.matcher(arq);
        // Validação sintática (regex) + semântica (faixa 1..60)
        if (!m.matches() || Integer.parseInt(m.group(1)) > TOTAL_ARQUIVOS) {
            System.out.println(MSG_ERRO_ENTRADA);
            historico.add(hora() + " [P" + id + "] Entrada rejeitada: \"" + arq + "\"");
            return null; // não propaga nada no anel (Caso 2 do PDF)
        }

        log("Usurio pediu busca de '" + arq + "'");
        CompletableFuture<Mensagem> futuro = new CompletableFuture<>();

        // Otimização: se o arquivo é local, nem envia mensagem
        if (arquivos.contains(arq)) {
            Mensagem local = Mensagem.busca(id, arq);
            local.caminho.add(id);
            log("RESULTADO: '" + arq + "' e local (P" + id + "), nenhuma mensagem enviada");
            futuro.complete(local.comoResposta(id, true));
            return futuro;
        }

        // Cria a BUSCA, registra o futuro pendente e envia ao sucessor
        Mensagem busca = Mensagem.busca(id, arq);
        busca.caminho.add(id); // primeiro elemento do caminho é a origem
        pendentes.put(busca.id, futuro);
        if (!enviar(busca, sucessor)) {
            pendentes.remove(busca.id);
            futuro.completeExceptionally(new IOException("sucessor P" + sucessor + " inacessivel"));
        }
        return futuro;
    }

    // Trata uma BUSCA recebida de outro nó
    private void processarBusca(Mensagem m) {
        int remetente = m.caminho.get(m.caminho.size() - 1); // último nó do caminho
        log("RECEBEU  BUSCA[" + m.id + "] '" + m.arquivo + "' de P" + remetente + " (origem P" + m.origem + ")");

        // Se a busca voltou à origem, o arquivo não existe no anel.
        // Sem isso, o anel entraria em loop infinito para arquivos inexistentes.
        if (m.origem == id) {
            log("A busca deu a volta no anel: arquivo inexistente");
            concluir(m.comoResposta(-1, false));
            return;
        }

        m.caminho.add(id); // registra passagem deste nó
        if (arquivos.contains(m.arquivo)) {
            // Responsável encontrado: responde pelo antecessor (caminho de volta)
            log("Possuo '" + m.arquivo + "'. Respondendo pelo antecessor P" + antecessor);
            enviar(m.comoResposta(id, true), antecessor);
        } else {
            // Não tem: repassa ao sucessor (sentido horário do anel)
            log("Nao possuo '" + m.arquivo + "'. Repassando ao sucessor P" + sucessor);
            enviar(m, sucessor);
        }
    }

    // Trata uma RESPOSTA recebida (volta pelo sentido anti-horário até a origem)
    private void processarResposta(Mensagem m) {
        log("RECEBEU  RESPOSTA[" + m.id + "] '" + m.arquivo + "' de P" + sucessor);
        if (m.origem == id) {
            // Sou a origem: completo o futuro e imprimo o resultado
            concluir(m);
        } else {
            // Não sou a origem: repasso ao antecessor
            log("Nao sou a origem. Repassando resposta ao antecessor P" + antecessor);
            enviar(m, antecessor);
        }
    }

    // Finaliza a busca: imprime resultado/caminho e completa o futuro pendente
    private void concluir(Mensagem m) {
        if (m.encontrado) {
            log("RESULTADO: '" + m.arquivo + "' encontrado em P" + m.responsavel
                    + " | caminho da busca: " + m.caminhoTexto());
        } else {
            log("RESULTADO: '" + m.arquivo + "' NAO encontrado no anel");
        }
        CompletableFuture<Mensagem> f = pendentes.remove(m.id);
        if (f != null) {
            f.complete(m);
        }
    }

    // Envia uma mensagem para outro nó via TCP (abre conexão, serializa, fecha)
    private boolean enviar(Mensagem m, int destino) {
        log("ENVIOU   " + m.tipo + "[" + m.id + "] '" + m.arquivo + "' para P" + destino);
        try (Socket s = new Socket(InetAddress.getLoopbackAddress(), PORTA_BASE + destino);
             ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
            out.writeObject(m);
            out.flush();
            return true;
        } catch (IOException e) {
            log("FALHA ao enviar para P" + destino + ": " + e.getMessage());
            return false;
        }
    }

    private static String hora() {
        return "[" + LocalTime.now().format(HORA) + "]";
    }

    // Log: imprime no console e guarda no histórico (o PDF exige os dois)
    private void log(String texto) {
        String linha = hora() + " [P" + id + "] " + texto;
        historico.add(linha);
        System.out.println(linha);
    }

    // Imprime o log armazenado deste nó
    public void imprimirHistorico() {
        System.out.println("---- Log de P" + id + " (" + historico.size() + " entradas) ----");
        synchronized (historico) {
            for (String linha : historico) {
                System.out.println(linha);
            }
        }
    }
}