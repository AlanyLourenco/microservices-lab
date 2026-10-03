package br.ufg.lab.pedido.mensageria;

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
 * Topologia do RabbitMQ vista pelo Pedido Service.
 *
 * <p>Como PRODUTOR de pedido.criado, o Pedido declara apenas o exchange
 * pedidos.exchange. Ele não declara a fila pedido.criado nem sabe quem a consome: essa
 * fila pertence ao consumidor (Pagamento Service).
 *
 * <p>Como CONSUMIDOR de pagamento.processado (Etapa 12), o Pedido declara a própria
 * fila, a liga ao exchange do Pagamento e define a DLQ para onde vão as mensagens que
 * falharem depois das retentativas.
 *
 * <p>As declarações são idempotentes. O Pagamento declara os mesmos exchanges com os
 * mesmos argumentos, então a ordem de inicialização dos serviços não importa.
 */
@Configuration
public class RabbitConfig {

    public static final String PEDIDOS_EXCHANGE = "pedidos.exchange";
    public static final String PEDIDO_CRIADO = "pedido.criado";

    public static final String PAGAMENTOS_EXCHANGE = "pagamentos.exchange";
    public static final String PAGAMENTO_PROCESSADO = "pagamento.processado";
    public static final String PAGAMENTO_PROCESSADO_DLQ = "pagamento.processado.dlq";

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

    @Bean
    Queue pagamentoProcessadoQueue() {
        return QueueBuilder.durable(PAGAMENTO_PROCESSADO)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(PAGAMENTO_PROCESSADO_DLQ)
                .build();
    }

    @Bean
    Queue pagamentoProcessadoDlq() {
        return QueueBuilder.durable(PAGAMENTO_PROCESSADO_DLQ).build();
    }

    @Bean
    Binding pagamentoProcessadoBinding() {
        return BindingBuilder.bind(pagamentoProcessadoQueue()).to(pagamentosExchange()).with(PAGAMENTO_PROCESSADO);
    }

    @Bean
    Binding pagamentoProcessadoDlqBinding() {
        return BindingBuilder.bind(pagamentoProcessadoDlq()).to(deadLetterExchange()).with(PAGAMENTO_PROCESSADO_DLQ);
    }

    /**
     * JSON nas mensagens. O tipo de destino é inferido do parâmetro do listener, e não
     * do cabeçalho __TypeId__. Assim os serviços não precisam compartilhar classes Java:
     * o contrato entre eles é só o formato JSON do evento.
     */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
