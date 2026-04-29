package com.example.marketplace.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * AOP-аспект для автоматического логирования всех методов сервисного слоя.
 *
 * ── Что такое AOP (Aspect-Oriented Programming)? ──────────────────────────────
 * Представь: у тебя 10 сервисов, и в каждый нужно добавить логирование времени работы.
 * Без AOP ты копируешь один и тот же код в каждый метод — это нарушает принцип DRY.
 * AOP позволяет вынести «сквозную логику» (логирование, метрики, безопасность)
 * в одно место — аспект — и применить её сразу ко всем нужным методам.
 *
 * ── Ключевые понятия ──────────────────────────────────────────────────────────
 * @Aspect      — этот класс является аспектом (Spring будет обрабатывать его особым образом).
 * @Component   — зарегистрировать как Spring-бин (иначе аспект не заработает).
 * Pointcut     — выражение, описывающее «где» применить аспект (какие методы перехватывать).
 * @Around      — «вокруг» метода: выполняется до И после, может менять результат.
 * @Before/@After — только до или только после (мы используем @Around для замера времени).
 * JoinPoint    — конкретная точка перехвата (конкретный вызов метода).
 *
 * ── Как это работает под капотом ─────────────────────────────────────────────
 * Spring создаёт прокси-обёртку вокруг каждого @Service-бина.
 * Когда CartController вызывает cartService.checkout(...), он на самом деле
 * вызывает прокси, который перехватывает вызов и передаёт его в logAround().
 * Мы можем выполнить код ДО, вызвать реальный метод через joinPoint.proceed(),
 * а потом выполнить код ПОСЛЕ — всё это прозрачно для вызывающего кода.
 */
@Slf4j
@Aspect
@Component
public class LoggingAspect {

    /**
     * Pointcut — описание того, какие методы перехватывать.
     *
     * execution(* com.example.marketplace.service.*.*(..))
     *           ↑   ↑                              ↑  ↑
     *  любой возврат  пакет service           любой класс  любые аргументы
     *
     * Pointcut вынесен в отдельный метод, чтобы переиспользовать его
     * в нескольких advice-аннотациях без дублирования строки.
     */
    @Pointcut("execution(* com.example.marketplace.service.*.*(..))")
    public void serviceLayer() {}

    /**
     * Advice типа @Around — выполняется ВОКРУГ перехваченного метода.
     *
     * ProceedingJoinPoint joinPoint — «точка перехвата»:
     *   getTarget().getClass().getSimpleName() → имя класса-сервиса
     *   getSignature().getName()               → имя метода
     *   proceed()                              → вызвать оригинальный метод
     *
     * Мы замеряем время выполнения и логируем:
     *   • → вход: класс, метод (без аргументов — они могут содержать пароли)
     *   • ← выход: сколько миллисекунд выполнялся метод
     *   • ✗ ошибка: имя исключения и сообщение
     */
    @Around("serviceLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        // Извлекаем имена класса и метода для удобного лога
        String className  = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        long startTime = System.currentTimeMillis();

        // DEBUG-уровень: виден только при spring.jpa.show-sql=true или logging.level.DEBUG
        log.debug("→ {}.{}()", className, methodName);

        try {
            // Вызываем оригинальный метод сервиса и получаем его результат
            Object result = joinPoint.proceed();

            long duration = System.currentTimeMillis() - startTime;
            // INFO-уровень: метод завершился успешно — логируем время
            log.info("← {}.{}() выполнен за {} мс", className, methodName, duration);

            return result;

        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - startTime;
            // WARN-уровень: метод бросил исключение — логируем тип ошибки
            log.warn("✗ {}.{}() ошибка за {} мс: {} — {}",
                    className, methodName, duration,
                    ex.getClass().getSimpleName(), ex.getMessage());
            // Пробрасываем исключение дальше: аспект не должен проглатывать ошибки
            throw ex;
        }
    }
}
