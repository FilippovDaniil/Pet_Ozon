package com.example.marketplace.payment;

import com.example.marketplace.entity.BnplInstallment;
import com.example.marketplace.repository.BnplInstallmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Планировщик авто-списания BNPL-взносов.
 *
 * Запускается раз в час. Находит все просроченные/сегодняшние PENDING-взносы
 * и пытается списать их через карточную привязку (paymentOrderBinding.do).
 *
 * Активируется аннотацией @EnableScheduling в SchedulerConfig.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BnplSchedulerService {

    private final BnplInstallmentRepository installmentRepo;
    private final BnplService               bnplService;

    /**
     * Каждый час проверяем взносы, у которых dueDate <= сегодня и status = PENDING.
     * fixedDelay = 3600000ms = 1 час.
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    public void processOverdueInstallments() {
        LocalDate today = LocalDate.now();
        List<BnplInstallment> due = installmentRepo.findDueInstallments(today);

        if (due.isEmpty()) return;

        log.info("SCHEDULER=BNPL_INSTALLMENT processing {} installments", due.size());
        for (BnplInstallment installment : due) {
            try {
                bnplService.processInstallment(installment);
            } catch (Exception e) {
                log.error("SCHEDULER=BNPL_INSTALLMENT_ERROR installmentId={} error={}",
                        installment.getId(), e.getMessage());
            }
        }
    }
}
