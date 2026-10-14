package org.example.pratica2.impl;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ImplServidorDiretorio implements Runnable {
    public Socket socketCliente;
    private static List<String> servidores = new ArrayList<>();
    private static int indexRoundRobin = 0;
    private Scanner entrada = null;
    private PrintStream saida = null;

    public ImplServidorDiretorio(Socket cliente) {
        this.socketCliente = cliente;
    }

    @Override
    public void run() {
        try {
            entrada = new Scanner(socketCliente.getInputStream());
            saida = new PrintStream(socketCliente.getOutputStream());

            if (entrada.hasNextLine()) {
                String cmd = entrada.nextLine();
                if (cmd.startsWith("/REGISTRAR")) {
                    String address = cmd.split(" ")[1];
                    synchronized (servidores) {
                        if (!servidores.contains(address)) {
                            servidores.add(address);
                        }
                    }
                    saida.println("OK");
                    System.out.println("[SERVIDOR REGISTRADO]: " + address);
                } else if (cmd.equals("/DESCOBRIR")) {
                    synchronized (servidores) {
                        if (servidores.isEmpty()) {
                            saida.println("[NENHUM SERVIDOR DISPONÍVEL]");
                        } else {
                            String target = servidores.get(indexRoundRobin);
                            indexRoundRobin = (indexRoundRobin + 1) % servidores.size(); // Garantir que o index seja circular, dentro do espaço [0 até (size - 1)]
                            saida.println(target);
                        }
                    }
                }
            }
            entrada.close();
            saida.close();
            socketCliente.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
