package br.ufg.lab.pagamento.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topologia do RabbitMQ vista pelo Pagamento Service.
 *
 * <p>Como CONSUMIDOR de pedido.criado, o Pagamento é dono da fila pedido.criado: ele a
 * declara durável e a liga a pedidos.exchange pela routing key pedido.criado (Etapa 5).
 * Mensagens que falharem depois das retentativas vão para pedido.criado.dlq, em vez de
 * serem descartadas ou reentregues para sempre.
 *
 * <p>Como PRODUTOR de pagamento.processado (Etapa 12), declara apenas o exchange
 * pagamentos.exchange. Quem consome o resultado (hoje, o Pedido) declara a própria fila.
 */
@Configuration
public class RabbitConfig {

    public static final String PEDIDOS_EXCHANGE = "pedidos.exchange";
    public static final String PEDIDO_CRIADO = "pedido.criado";
    public static final String PEDIDO_CRIADO_DLQ = "pedido.criado.dlq";

    public static final String PAGAMENTOS_EXCHANGE = "pagamentos.exchange";
    public static final String PAGAMENTO_PROCESSADO = "pagamento.processado";

    public static final String DEAD_LETTER_EXCHANGE = "lab.dlx";

    @Bean
    TopicExchange pedidosExchange() {
        return ExchangeBuilder.topicExchange(PEDIDOS_EXCHANGE).durable(true).build();
    }

    @Bean
    TopicExchange pagamentosExchange() {
        return ExchangeBuilder.topicExchange(PAGAMENTOS_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    /** Durável: a fila e suas mensagens persistentes sobrevivem a reinícios (Etapas 8 e 9). */
    @Bean
    Queue pedidoCriadoQueue() {
        return QueueBuilder.durable(PEDIDO_CRIADO)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PEDIDO_CRIADO_DLQ)
                .build();
    }

    @Bean
    Queue pedidoCriadoDlq() {
        return QueueBuilder.durable(PEDIDO_CRIADO_DLQ).build();
    }

    @Bean
    Binding pedidoCriadoBinding() {
        return BindingBuilder.bind(pedidoCriadoQueue()).to(pedidosExchange()).with(PEDIDO_CRIADO);
    }

    @Bean
    Binding pedidoCriadoDlqBinding() {
        return BindingBuilder.bind(pedidoCriadoDlq()).to(deadLetterExchange()).with(PEDIDO_CRIADO_DLQ);
    }

    /** JSON nas mensagens. O tipo é inferido do parâmetro do listener, não de classes compartilhadas. */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
