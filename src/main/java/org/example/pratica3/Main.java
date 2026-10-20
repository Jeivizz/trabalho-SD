package org.example.pratica3;

import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws Exception {
        // Instancia e sobe os 6 nós (P0..P5), cada um em sua porta
        NoAnel[] nos = new NoAnel[NoAnel.TOTAL_NOS];
        for (int i = 0; i < nos.length; i++) {
            nos[i] = new NoAnel(i);
            nos[i].iniciar();
        }
        Thread.sleep(300); // dá tempo dos ServerSockets abrirem

        // CASO 1 do PDF: P0 busca arquivo35.ext -> deve ir P0->P1->P2->P3
        titulo("CASO 1 - Busca válida: origem P0, arquivo35.ext (esperado: P0->P1->P2->P3)");
        executar(nos[0], "arquivo35.ext");

        // CASO 2a: entrada fora do intervalo 1..60 -> rejeitada sem propagar
        titulo("CASO 2a - Entrada inválida: origem P1, arquivo99.ext (fora de 1-60)");
        executar(nos[1], "arquivo99.ext");

        // CASO 2b: entrada com formato inválido -> rejeitada sem propagar
        titulo("CASO 2b - Entrada inválida: origem P4, arquivoABC.ext");
        executar(nos[4], "arquivoABC.ext");

        // O PDF pede que cada processo armazene e imprima seu log; mostramos o de P3
        titulo("LOG ARMAZENADO de P3 (cada processo guarda e imprime suas mensagens)");
        nos[3].imprimirHistorico();

        // Encerra todos os nós
        for (NoAnel n : nos) {
            n.parar();
        }
    }

    // Dispara a busca e espera até 5s pelo resultado (timeout evita travar a demo)
    private static void executar(NoAnel no, String entrada) throws InterruptedException {
        var f = no.buscar(entrada); // null se entrada inválida
        if (f != null) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Busca sem resposta: " + e.getMessage());
            }
        }
        Thread.sleep(300); // pausa didática para o console ficar legível
    }

    private static void titulo(String s) {
        System.out.println("\n===== " + s + " =====");
    }
}