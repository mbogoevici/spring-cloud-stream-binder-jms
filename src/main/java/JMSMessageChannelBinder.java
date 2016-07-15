import org.springframework.cloud.stream.binder.*;
import org.springframework.integration.endpoint.AbstractEndpoint;
import org.springframework.integration.endpoint.EventDrivenConsumer;
import org.springframework.integration.jms.ChannelPublishingJmsMessageListener;
import org.springframework.integration.jms.JmsMessageDrivenEndpoint;
import org.springframework.integration.jms.JmsSendingMessageHandler;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.stereotype.Component;

import javax.jms.ConnectionFactory;
import javax.jms.JMSException;

public class JMSMessageChannelBinder extends AbstractBinder<MessageChannel, ConsumerProperties, ProducerProperties> {

    private BindingFactory bindingFactory;
    private JmsTemplate template;
    private ListenerContainerFactory listenerContainerFactory;

    public JMSMessageChannelBinder(ConnectionFactory factory, JmsTemplate template) throws JMSException {
        this(new ListenerContainerFactory(factory), new BindingFactory(), template);
    }

    public JMSMessageChannelBinder(ListenerContainerFactory listenerContainerFactory, BindingFactory bindingFactory, JmsTemplate template) throws JMSException {
        this.bindingFactory = bindingFactory;
        this.template = template;
        this.listenerContainerFactory = listenerContainerFactory;
    }

    /**
     * JMS Consumer - consumes JMS messages and writes them to the inputTarget, so it's an input to our application (Sink.INPUT)
     */
    @Override
    protected Binding<MessageChannel> doBindConsumer(String name, String group, MessageChannel inputTarget, ConsumerProperties properties) {
        AbstractMessageListenerContainer listenerContainer = listenerContainerFactory.build(name);
        DefaultBinding<MessageChannel> binding = bindingFactory.build(name, group, inputTarget, listenerContainer);
        return binding;
    }

    /**
     * JMS Producer - consumes Spring from the outboundBindTarget messages and writes them to JMS, so it's an output from our application (Source.OUTPUT)
     */
    @Override
    protected Binding<MessageChannel> doBindProducer(String name, MessageChannel outboundBindTarget, ProducerProperties properties) {
        template.setPubSubDomain(true);

        JmsSendingMessageHandler handler = new JmsSendingMessageHandler(template);
        handler.setDestinationName(name);

        AbstractEndpoint consumer = new EventDrivenConsumer((SubscribableChannel) outboundBindTarget, handler);

        consumer.setBeanFactory(getBeanFactory());
        consumer.setBeanName("outbound." + name);
        consumer.afterPropertiesSet();
        consumer.start();

        return new DefaultBinding<>(name, null, outboundBindTarget, consumer);
    }


    @Component
    public static class ListenerContainerFactory {

        private ConnectionFactory factory;

        public ListenerContainerFactory(ConnectionFactory factory) {
            this.factory = factory;
        }

        public AbstractMessageListenerContainer build(String name) {
            AbstractMessageListenerContainer listenerContainer = new SimpleMessageListenerContainer();
            listenerContainer.setDestinationName(name);
            listenerContainer.setPubSubDomain(true);
            listenerContainer.setConnectionFactory(factory);
            return listenerContainer;
        }
    }


    public static class BindingFactory {

        public DefaultBinding<MessageChannel> build(String name, String group, MessageChannel inputTarget, AbstractMessageListenerContainer listenerContainer) {
            ChannelPublishingJmsMessageListener listener = new ChannelPublishingJmsMessageListener();
            listener.setRequestChannel(inputTarget);

            AbstractEndpoint endpoint = new JmsMessageDrivenEndpoint(listenerContainer, listener);
            DefaultBinding<MessageChannel> binding = new DefaultBinding<>(name, group, inputTarget, endpoint);
            endpoint.setBeanName("inbound." + name);
            endpoint.start();
            return binding;
        }
    }
}
