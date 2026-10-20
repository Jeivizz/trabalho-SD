package org.example.pratica1;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class DnsServer {

    // Porta 5300 em vez de 5353 porque 5353 é usada pelo mDNS em muitos SOs
    // (evita conflito de porta ao rodar a demo)
    public static final int PORTA_PADRAO = 5300;

    // Regex para validar IPv4 (0-255 em cada octeto) — tratamento de erro extra
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    // Formato de hora para os logs (com milissegundos, útil para mostrar ordem dos eventos)
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    // Socket UDP do servidor (escuta em uma porta fixa)
    private final DatagramSocket socket;

    // Tabela nome -> IP. ConcurrentHashMap porque várias threads do pool acessam
    private final Map<String, String> tabela = new ConcurrentHashMap<>();

    // Conjunto de endereços (IP:porta) dos clientes que fizeram REGISTER
    // newKeySet() cria um Set concorrente — seguro para o pool de threads
    private final Set<SocketAddress> requisitantes = ConcurrentHashMap.newKeySet();

    // Pool de threads: cada pacote recebido é tratado em uma thread separada
    // (assim o servidor não bloqueia enquanto responde um cliente)
    private final ExecutorService pool;
    // Flag para encerrar o loop principal
    private volatile boolean ativo = true;

    public DnsServer(int porta) throws SocketException {
        this.socket = new DatagramSocket(porta);
        // Carga inicial exigida pela questão: servidor1..10 -> 192.168.0.10..100
        for (int i = 1; i <= 10; i++) {
            tabela.put("servidor" + i, "192.168.0." + (i * 10));
        }
        // Cria threads daemon (não impedem a JVM de encerrar)
        this.pool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
    }

    // Loop principal: recebe pacotes UDP e delega o tratamento ao pool
    public void executar() {
        log("Mini-DNS escutando em UDP/" + socket.getLocalPort() + " com " + tabela.size() + " registros");
        while (ativo) {
            DatagramPacket pacote = new DatagramPacket(new byte[1024], 1024);
            try {
                socket.receive(pacote); // bloqueia até chegar um datagrama
            } catch (IOException e) {
                if (ativo) {
                    log("Erro de E/S ao receber: " + e.getMessage());
                }
                break; // se o socket foi fechado (parar()), sai do loop
            }
            // Cada pacote é processado em paralelo no pool
            pool.execute(() -> tratar(pacote));
        }
    }

    public void parar() {
        ativo = false;
        socket.close(); // fecha o socket, faz o receive() lançar exceção e sair do loop
    }

    // Interpreta o comando recebido e despacha para o handler correto
    private void tratar(DatagramPacket pacote) {
        // Converte os bytes do pacote em String (respeitando offset e length)
        String msg = new String(pacote.getData(), pacote.getOffset(), pacote.getLength(),
                StandardCharsets.UTF_8).trim();
        SocketAddress remetente = pacote.getSocketAddress();
        log("RECEBIDO de " + remetente + ": \"" + msg + "\"");

        String[] t = msg.split("\\s+"); // separa por espaços
        switch (t[0].toUpperCase()) {
            case "REGISTER":
                // Cliente se registra para receber NOTIFY quando houver update
                requisitantes.add(remetente);
                enviar("REGISTRADO", remetente);
                break;

            case "UNREGISTER":
                requisitantes.remove(remetente);
                enviar("DESREGISTRADO", remetente);
                break;

            case "RESOLVE":
                // Resolução de nome: RESOLVE <nome> -> RESOLVIDO <nome> <ip>
                if (t.length != 2) {
                    enviar("ERRO REQUISICAO_INVALIDA", remetente);
                    break;
                }
                String nome = t[1].toLowerCase();
                String ip = tabela.get(nome);
                enviar(ip != null ? "RESOLVIDO " + nome + " " + ip : "ERRO NAO_ENCONTRADO " + nome, remetente);
                break;

            case "UPDATE":
                atualizar(t, remetente);
                break;

            default:
                enviar("ERRO REQUISICAO_INVALIDA", remetente);
        }
    }

    // Atualiza o IP de um nome e propaga NOTIFY para todos os requisitantes
    private void atualizar(String[] t, SocketAddress registrador) {
        if (t.length != 3) {
            enviar("ERRO REQUISICAO_INVALIDA", registrador);
            return;
        }
        String nome = t[1].toLowerCase();
        String novoIp = t[2];

        // Valida se o nome existe
        if (!tabela.containsKey(nome)) {
            enviar("ERRO NAO_ENCONTRADO " + nome, registrador);
            return;
        }
        // Valida o formato do IP com regex (tratamento de erro extra)
        if (!IPV4.matcher(novoIp).matches()) {
            enviar("ERRO IP_INVALIDO " + novoIp, registrador);
            return;
        }

        // Faz o update no mapa (put retorna o valor antigo)
        String antigo = tabela.put(nome, novoIp);
        log("ATUALIZADO " + nome + ": " + antigo + " -> " + novoIp);
        // Confirma para quem mandou o update
        enviar("ATUALIZADO " + nome + " " + novoIp, registrador);

        // *** BINDING DINÂMICO: propaga a mudança para todos os clientes registrados ***
        log("Propagando NOTIFY para " + requisitantes.size() + " requisitante(s)");
        for (SocketAddress cliente : requisitantes) {
            enviar("NOTIFY " + nome + " " + novoIp, cliente);
        }
    }

    // Envia uma resposta/datagrama para um destino
    private void enviar(String texto, SocketAddress destino) {
        byte[] dados = texto.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(dados, dados.length, destino));
            log("ENVIADO para " + destino + ": \"" + texto + "\"");
        } catch (IOException e) {
            log("Falha ao enviar para " + destino + ": " + e.getMessage());
        }
    }

    // Log padronizado com timestamp
    private static void log(String texto) {
        System.out.println("[" + LocalTime.now().format(HORA) + "] [DNS] " + texto);
    }

    public static void main(String[] args) throws Exception {
        int porta = args.length > 0 ? Integer.parseInt(args[0]) : PORTA_PADRAO;
        new DnsServer(porta).executar();
    }
}