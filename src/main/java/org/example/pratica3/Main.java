package org.example.pratica3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QUESTÃO 3 - Sobe os 6 nós (P0..P5) em threads de uma mesma JVM, cada um com
 * seu ServerSocket TCP (comunicação real por sockets em localhost), executa os
 * casos de teste do enunciado e depois abre um console interativo.
 *
 * Console interativo:
 *   P<n> <arquivo>   -> o processo Pn inicia uma busca (ex.: P2 arquivo10.ext)
 *   log P<n>         -> imprime o histórico de mensagens do processo Pn
 *   sair
 */
public class Main {

    public static void main(String[] args) throws Exception {
        NoAnel[] nos = new NoAnel[NoAnel.TOTAL_NOS];
        for (int i = 0; i < nos.length; i++) {
            nos[i] = new NoAnel(i);
            nos[i].iniciar();
        }
        Thread.sleep(300);

        titulo("CASO 1 - Busca válida: origem P0, arquivo35.ext (esperado: P0->P1->P2->P3)");
        executar(nos[0], "arquivo35.ext");

        titulo("CASO 2a - Entrada inválida: origem P1, arquivo99.ext (fora de 1-60)");
        executar(nos[1], "arquivo99.ext");

        titulo("CASO 2b - Entrada inválida: origem P4, arquivoABC.ext");
        executar(nos[4], "arquivoABC.ext");

        titulo("EXTRA 1 - Busca que dá a volta no anel: origem P4, arquivo5.ext (P4->P5->P0)");
        executar(nos[4], "arquivo5.ext");

        titulo("EXTRA 2 - Arquivo local: origem P2, arquivo25.ext (sem mensagens)");
        executar(nos[2], "arquivo25.ext");

        titulo("LOG ARMAZENADO de P3 (cada processo guarda e imprime suas mensagens)");
        nos[3].imprimirHistorico();

        titulo("MODO INTERATIVO - ex.: P2 arquivo10.ext | log P0 | sair");
        interativo(nos);

        for (NoAnel n : nos) {
            n.parar();
        }
    }

    private static void executar(NoAnel no, String entrada) throws InterruptedException {
        CompletableFuture<Mensagem> f = no.buscar(entrada);
        if (f != null) {
            try {
                f.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                System.out.println("Busca sem resposta: " + e.getMessage());
            }
        }
        Thread.sleep(200); // deixa os logs finais aparecerem antes do próximo caso
    }

    private static void interativo(NoAnel[] nos) throws Exception {
        Pattern busca = Pattern.compile("^[Pp](\\d+)\\s+(.+)$");
        Pattern log = Pattern.compile("^log\\s+[Pp](\\d+)$");
        java.io.BufferedReader teclado = new java.io.BufferedReader(new java.io.InputStreamReader(System.in));
        String linha;
        while ((linha = teclado.readLine()) != null) {
            linha = linha.trim();
            if (linha.equals("sair")) {
                break;
            }
            Matcher mb = busca.matcher(linha);
            Matcher ml = log.matcher(linha);
            if (mb.matches()) {
                NoAnel no = obter(nos, mb.group(1));
                if (no != null) {
                    executar(no, mb.group(2));
                }
            } else if (ml.matches()) {
                NoAnel no = obter(nos, ml.group(1));
                if (no != null) {
                    no.imprimirHistorico();
                }
            } else if (!linha.isEmpty()) {
                System.out.println("Comando inválido. Use: P<0-5> <arquivo> | log P<0-5> | sair");
            }
        }
    }

    private static NoAnel obter(NoAnel[] nos, String idTexto) {
        try {
            int id = Integer.parseInt(idTexto);
            if (id >= 0 && id < nos.length) {
                return nos[id];
            }
        } catch (NumberFormatException ignorado) {
            // cai no erro abaixo
        }
        System.out.println("Processo inexistente (use P0 a P" + (nos.length - 1) + ")");
        return null;
    }

    private static void titulo(String s) {
        System.out.println("\n===== " + s + " =====");
    }
}

