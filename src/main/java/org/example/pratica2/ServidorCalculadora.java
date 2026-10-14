package org.example.pratica2;

import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorCalculadora {
    static final String IP_DIRETORIO = "127.0.0.1";
    static final int PORTA_DIRETORIO = 9000;
    static final int QTD_SERVERS = 3;

    int porta;
    String nome;



    public ServidorCalculadora(String nome, int porta) {
        this.nome = nome;
        this.porta = porta;
        this.registrarDiretorio();
        this.rodar();
    }

    private void registrarDiretorio() {
        try {
            Socket socket = new Socket(IP_DIRETORIO, PORTA_DIRETORIO);
            PrintStream saida = new PrintStream(socket.getOutputStream());
            saida.println("/REGISTRAR " + IP_DIRETORIO + ":" + this.porta);
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void rodar() {
        try {
            ServerSocket socketServidor = new ServerSocket(porta);
            System.out.println(nome + " rodando na porta [" + socketServidor.getLocalPort() + "]");
            while (true) {
                Socket cliente = socketServidor.accept();
                ImplServidorCalculadora servidor = new ImplServidorCalculadora(cliente, nome);
                Thread t = new Thread(servidor);
                t.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new Thread(() -> new ServidorCalculadora("CalcServer-1", 9001)).start();
        new Thread(() -> new ServidorCalculadora("CalcServer-2", 9002)).start();
        new Thread(() -> new ServidorCalculadora("CalcServer-3", 9003)).start();
    }
}
