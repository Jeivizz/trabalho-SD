package org.example.pratica1;

import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        int porta = DnsServer.PORTA_PADRAO;

        // Sobe o DNS em uma thread daemon (roda em background)
        DnsServer dns = new DnsServer(porta);
        Thread t = new Thread(dns::executar, "dns");
        t.setDaemon(true);
        t.start();
        pausa(300); // dá tempo do socket abrir

        // PASSO 1 — três clientes requisitantes fazem consultas iniciais
        titulo("PASSO 1 - Consultas iniciais dos 3 requisitantes");
        ClienteRequisitante c1 = new ClienteRequisitante("Cliente1", "localhost", porta);
        ClienteRequisitante c2 = new ClienteRequisitante("Cliente2", "localhost", porta);
        ClienteRequisitante c3 = new ClienteRequisitante("Cliente3", "localhost", porta);
        pausa(300); // espera os REGISTER chegarem

        c1.resolver("servidor1");
        c1.resolver("servidor4");
        c2.resolver("servidor9");
        c2.resolver("servidor1");
        c3.resolver("servidor4");
        c3.resolver("servidor9");
        c3.resolver("servidor10");
        c1.resolver("servidor99"); // nome inexistente -> testa erro NAO_ENCONTRADO
        pausa(600); // espera respostas

        // PASSO 2 — registrador atualiza servidor1, servidor4 e servidor9
        // Isso demonstra o BINDING DINÂMICO: servidor propaga NOTIFY aos clientes
        titulo("PASSO 2 - Registrador atualiza servidor1, servidor4 e servidor9");
        ClienteRegistrador reg = new ClienteRegistrador("localhost", porta);
        System.out.println("  [Registrador] " + reg.atualizar("servidor1", "10.0.0.11"));
        System.out.println("  [Registrador] " + reg.atualizar("servidor4", "10.0.0.44"));
        System.out.println("  [Registrador] " + reg.atualizar("servidor9", "10.0.0.99"));
        pausa(600); // espera os NOTIFY chegarem

        // PASSO 3 — testa tratamento de erros do servidor
        titulo("PASSO 3 - Erros tratados pelo servidor");
        System.out.println("  [Registrador] " + reg.atualizar("servidor1", "999.1.1.1")); // IP inválido
        System.out.println("  [Registrador] " + reg.atualizar("servidor77", "10.0.0.7")); // nome inexistente
        pausa(400);

        // PASSO 4 — imprime o cache de cada cliente para provar que receberam NOTIFY
        titulo("PASSO 4 - Cache de cada requisitante após as notificações");
        mostrar("Cliente1", c1);
        mostrar("Cliente2", c2);
        mostrar("Cliente3", c3);

        // PASSO 5 — nova consulta confirma que o DNS guardou o valor novo
        titulo("PASSO 5 - Nova consulta (confirma que o DNS guarda o valor novo)");
        c3.resolver("servidor1");
        pausa(400);

        // Encerra tudo
        c1.fechar();
        c2.fechar();
        c3.fechar();
        reg.fechar();
        dns.parar();
    }

    // Imprime o cache de um cliente (nome=ip em ordem alfabética)
    private static void mostrar(String nome, ClienteRequisitante c) {
        System.out.print("  " + nome + ": ");
        for (Map.Entry<String, String> e : c.copiaDoCache().entrySet()) {
            System.out.print(e.getKey() + "=" + e.getValue() + "  ");
        }
        System.out.println();
    }

    private static void titulo(String s) {
        System.out.println("\n===== " + s + " =====");
    }

    // Pausa só para fins didáticos de demonstração (sincroniza a ordem dos eventos no console)
    private static void pausa(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}