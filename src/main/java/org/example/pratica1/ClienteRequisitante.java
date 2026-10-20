package org.example.pratica1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class ClienteRequisitante {

    private final String nome; // nome do cliente (Cliente1, Cliente2...)
    private final DatagramSocket socket;
    private final InetSocketAddress servidor; // endereço do DNS

    // Cache local: nome -> IP. ConcurrentHashMap porque a thread "ouvinte"
    // escreve nele enquanto o main lê (ex.: comando "cache")
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private volatile boolean ativo = true;

    public ClienteRequisitante(String nome, String host, int portaServidor) throws IOException {
        this.nome = nome;
        // Um único socket é usado tanto para enviar quanto para receber.
        // Isso é essencial: o servidor guarda o IP:porta do remetente do REGISTER
        // e depois manda NOTIFY para essa mesma porta efêmera.
        this.socket = new DatagramSocket();
        this.servidor = new InetSocketAddress(InetAddress.getByName(host), portaServidor);

        // Thread daemon que fica escutando respostas/notificações em background
        Thread ouvinte = new Thread(this::escutar, nome + "-ouvinte");
        ouvinte.setDaemon(true);
        ouvinte.start();

        // Registra-se no servidor para receber atualizações (binding dinâmico)
        enviar("REGISTER");
    }

    public void resolver(String nomeServidor) {
        enviar("RESOLVE " + nomeServidor);
    }

    public String consultarCache(String nomeServidor) {
        return cache.get(nomeServidor);
    }

    // Cópia ordenada do cache só para impressão bonita (TreeMap)
    public Map<String, String> copiaDoCache() {
        return new TreeMap<>(cache);
    }

    public void fechar() {
        enviar("UNREGISTER"); // avisa o servidor para não mandar mais NOTIFY
        ativo = false;
        socket.close(); // fecha o socket, a thread ouvinte sai do receive()
    }

    private void enviar(String texto) {
        byte[] dados = texto.getBytes(StandardCharsets.UTF_8);
        try {
            socket.send(new DatagramPacket(dados, dados.length, servidor));
        } catch (IOException e) {
            imprimir("Falha ao enviar \"" + texto + "\": " + e.getMessage());
        }
    }

    // Loop da thread ouvinte: recebe pacotes e interpreta
    private void escutar() {
        while (ativo) {
            DatagramPacket pacote = new DatagramPacket(new byte[1024], 1024);
            try {
                socket.receive(pacote); // bloqueia até chegar algo
            } catch (IOException e) {
                break; // socket fechado -> sai
            }
            String texto = new String(pacote.getData(), 0, pacote.getLength(), StandardCharsets.UTF_8).trim();
            interpretar(texto);
        }
    }

    // Trata cada tipo de mensagem vinda do servidor
    private void interpretar(String texto) {
        String[] t = texto.split("\\s+");
        if (t[0].equals("RESOLVIDO") && t.length == 3) {
            // Resposta a um RESOLVE: guarda no cache
            cache.put(t[1], t[2]);
            imprimir("Resposta do DNS: " + t[1] + " -> " + t[2]);
        } else if (t[0].equals("NOTIFY") && t.length == 3) {
            // *** BINDING DINÂMICO: servidor avisou que o IP mudou ***
            String antigo = cache.put(t[1], t[2]); // atualiza o cache
            imprimir("*** NOTIFICAÇÃO: " + t[1] + " mudou"
                    + (antigo != null ? " de " + antigo : " (não estava no cache)")
                    + " para " + t[2]);
        } else if (t[0].equals("REGISTRADO")) {
            imprimir("Registrado no servidor para receber atualizações");
        } else if (t[0].equals("DESREGISTRADO")) {
            imprimir("Desregistrado; não receberá mais atualizações");
        } else if (t[0].equals("ERRO")) {
            imprimir("Erro do servidor: " + texto.substring(4).trim());
        } else {
            imprimir("Mensagem desconhecida: \"" + texto + "\"");
        }
    }

    private void imprimir(String texto) {
        System.out.println("  [" + nome + "] " + texto);
    }

    public static void main(String[] args) throws Exception {
        String nome = args.length > 0 ? args[0] : "Cliente1";
        String host = args.length > 1 ? args[1] : "localhost";
        int porta = args.length > 2 ? Integer.parseInt(args[2]) : DnsServer.PORTA_PADRAO;

        ClienteRequisitante c = new ClienteRequisitante(nome, host, porta);
        System.out.println("Comandos: resolver <nome> | cache | sair");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String linha;
        while ((linha = in.readLine()) != null) {
            linha = linha.trim();
            if (linha.equals("sair")) {
                break;
            } else if (linha.equals("cache")) {
                System.out.println(c.copiaDoCache());
            } else if (linha.startsWith("resolver ") && linha.length() > 9) {
                c.resolver(linha.substring(9).trim());
            } else if (!linha.isEmpty()) {
                System.out.println("Comando inválido. Use: resolver <nome> | cache | sair");
            }
        }
        c.fechar();
    }
}