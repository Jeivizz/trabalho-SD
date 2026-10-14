package org.example.pratica2;
import org.example.pratica2.impl.ImplServidorDiretorio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServidorDiretorio {
    int porta;

    public ServidorDiretorio(int porta) {
        this.porta = porta;
        this.rodar();
    }

    private void rodar() {
        try {
            ServerSocket socketServidor = new ServerSocket(porta);
            System.out.println("Servidor de Diretório rodando na porta [" + socketServidor.getLocalPort() + "]");
            while(true) {
                Socket cliente = socketServidor.accept();
                ImplServidorDiretorio servidor = new ImplServidorDiretorio(cliente);
                Thread t = new Thread(servidor);
                t.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new ServidorDiretorio(9000);
    }
}
