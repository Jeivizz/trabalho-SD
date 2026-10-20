package org.example.pratica1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class ClienteRegistrador {

    // Timeout para não ficar travado esperando resposta do servidor
    private static final int TIMEOUT_MS = 2000;

    private final DatagramSocket socket;
    private final InetSocketAddress servidor;

    public ClienteRegistrador(String host, int portaServidor) throws IOException {
        this.socket = new DatagramSocket();
        // define timeout de recepção (2s) — se o servidor não responder, lança SocketTimeoutException
        this.socket.setSoTimeout(TIMEOUT_MS);
        this.servidor = new InetSocketAddress(InetAddress.getByName(host), portaServidor);
    }

    // Envia UPDATE <nome> <ip> e espera confirmação
    public String atualizar(String nome, String ip) throws IOException {
        byte[] dados = ("UPDATE " + nome + " " + ip).getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(dados, dados.length, servidor));

        DatagramPacket resposta = new DatagramPacket(new byte[1024], 1024);
        try {
            socket.receive(resposta); // espera até 2s
        } catch (SocketTimeoutException e) {
            return "ERRO TIMEOUT (sem resposta do servidor em " + TIMEOUT_MS + " ms)";
        }
        return new String(resposta.getData(), 0, resposta.getLength(), StandardCharsets.UTF_8).trim();
    }

    public void fechar() {
        socket.close();
    }

    // main interativo: permite digitar "atualizar <nome> <ip>" ou "sair"
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int porta = args.length > 1 ? Integer.parseInt(args[1]) : DnsServer.PORTA_PADRAO;

        ClienteRegistrador r = new ClienteRegistrador(host, porta);
        System.out.println("Comandos: atualizar <nome> <ip> | sair");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String linha;
        while ((linha = in.readLine()) != null) {
            linha = linha.trim();
            if (linha.equals("sair")) {
                break;
            }
            String[] t = linha.split("\\s+");
            if (t.length == 3 && t[0].equals("atualizar")) {
                System.out.println("  [Registrador] " + r.atualizar(t[1], t[2]));
            } else if (!linha.isEmpty()) {
                System.out.println("Comando inválido. Use: atualizar <nome> <ip> | sair");
            }
        }
        r.fechar();
    }
}