package com.estudos.orderservice.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base dos testes de integração: sobe a aplicação inteira contra MySQL e Kafka reais.
 *
 * Os containers são "singletons": iniciados uma vez no bloco estático e reaproveitados por
 * todas as classes de teste (e por todos os contextos Spring) da execução. Não usamos
 * {@code @Container}/{@code @Testcontainers} de propósito: eles parariam os containers ao fim
 * de cada classe, quebrando o contexto Spring que fica em cache entre as classes.
 * O Ryuk (do Testcontainers) remove os containers quando a JVM termina.
 *
 * {@code @ServiceConnection} faz o Spring Boot apontar datasource e Kafka para os containers,
 * sobrescrevendo o que vier do application.yml / .env.
 *
 * Jobs agendados desligados: o contexto fica em cache entre as classes e um relay rodando em
 * segundo plano publicaria/travaria linhas da outbox no meio de outros testes. Quem precisa
 * do relay chama {@code OutboxRelay.relayPending()} diretamente. Não sobrescreva propriedades
 * nas subclasses: cada combinação diferente cria um novo contexto Spring.
 */
@SpringBootTest(properties = "app.outbox.relay.enabled=false")
public abstract class AbstractIntegrationTest {

    // mesmas versões do ambiente local (docker ps): mysql:8.4 e apache/kafka:4.1.0
    @ServiceConnection
    protected static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("order_db");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0");

    static {
        MYSQL.start();
        KAFKA.start();
    }
}
