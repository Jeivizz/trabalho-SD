package org.example.pratica2;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.util.Scanner;

public class ImplServidorCalculadora implements Runnable {
    private Socket socketCliente;
    private String nomeServidor;
    private Scanner entrada = null;
    private PrintStream saida = null;

    public ImplServidorCalculadora(Socket cliente, String nomeServidor) {
        this.socketCliente = cliente;
        this.nomeServidor = nomeServidor;
    }

    @Override
    public void run() {
        try {
            entrada = new Scanner(socketCliente.getInputStream());
            saida = new PrintStream(socketCliente.getOutputStream());

            if (entrada.hasNextLine()) {
                String requisicao = entrada.nextLine();
                String[] partes = requisicao.split(" ");

                try {
                    String operacao = partes[0];
                    double a = Double.parseDouble(partes[1]);
                    double b = Double.parseDouble(partes[2]);
                    double resultado = 0;

                    switch (operacao) {
                        case "SOMA": resultado = a + b; break;
                        case "SUBTRACAO": resultado = a - b; break;
                        case "MULTIPLICACAO": resultado = a * b; break;
                        case "DIVISAO":
                            if (b == 0) throw new ArithmeticException("Divisão por zero");
                            resultado = a / b;
                            break;
                        default:
                            throw new IllegalArgumentException("Operação desconhecida");
                    }
                    saida.println("[" + nomeServidor + "] Resultado: " + resultado);

                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    saida.println("[" + nomeServidor + "] [ERRO]: Entrada inválida. Formato esperado: OPERACAO N1 N2");
                } catch (ArithmeticException | IllegalArgumentException e) {
                    saida.println("[" + nomeServidor + "] [ERRO]: " + e.getMessage());
                }
            }
            entrada.close();
            saida.close();
            socketCliente.close();
        } catch (IOException e) {
            System.out.println("Erro na calculadora: " + e.getMessage());
        }
    }
}