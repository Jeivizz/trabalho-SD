package org.example.pratica2.impl;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

public class ImplCliente implements Runnable {
    String idCliente, operacao, entradaBruta;

    public ImplCliente(String idCliente, String operacao, String entradaBruta) {
        this.idCliente = idCliente;
        this.operacao = operacao;
        this.entradaBruta = entradaBruta;
    }

    @Override
    public void run() {
        String endereco = descobrirServidor();
        if (endereco == null || endereco.equals("[NENHUM SERVIDOR DISPONÍVEL]")) {
            System.out.println(idCliente + " - [NENHUM SERVIDOR DISPONÍVEL]");
            return;
        }

        String[] partes = endereco.split(":");
        try {
            Socket socket = new Socket(partes[0], Integer.parseInt(partes[1]));
            PrintStream saida = new PrintStream(socket.getOutputStream());
            Scanner entrada = new Scanner(socket.getInputStream());

            String comandoCompleto = operacao + " " + entradaBruta;
            saida.println(comandoCompleto);

            if (entrada.hasNextLine()) {
                String resp = entrada.nextLine();
                synchronized (System.out) {
                    System.out.println(idCliente + " solicitou [" + comandoCompleto + "] a [" + endereco + "]");
                    System.out.println(resp);
                    System.out.println("-----------------------------------------------------------------------------------------------------");

                }

            }
            entrada.close();
            saida.close();
            socket.close();
        } catch (IOException e) {
            synchronized (System.out) {
                System.out.println("[ERRO]: " + e.getMessage());
            }

        }
    }

    private String descobrirServidor() {
        try {
            Socket socket = new Socket("127.0.0.1", 9000);
            PrintStream saida = new PrintStream(socket.getOutputStream());
            Scanner entrada = new Scanner(socket.getInputStream());

            saida.println("/DESCOBRIR");
            String resposta = entrada.hasNextLine() ? entrada.nextLine() : null;

            entrada.close();
            saida.close();
            socket.close();
            return resposta;
        } catch (IOException e) {
            return null;
        }
    }
}