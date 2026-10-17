package br.org.pascom.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga as tarefas agendadas (lembretes, escalas do mês, Instagram, liturgia...). Sempre ligado, menos
 * nos testes automáticos (app.agendamentos=false), pra eles não dispararem lembretes nem irem à internet.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.agendamentos", havingValue = "true", matchIfMissing = true)
public class AgendamentoConfig {
}
