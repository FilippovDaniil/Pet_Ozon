package com.example.marketplace.service;

import com.example.marketplace.dto.request.PickupPointRequest;
import com.example.marketplace.dto.response.PickupPointResponse;
import com.example.marketplace.entity.PickupPoint;
import com.example.marketplace.exception.ResourceNotFoundException;
import com.example.marketplace.repository.PickupPointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Управление справочником точек самовывоза.
 *
 * Чтение активных точек доступно клиентам (выбор при оформлении).
 * Создание/изменение/удаление — только администратору (@PreAuthorize + /api/admin/**).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickupPointService {

    private final PickupPointRepository repo;

    /** Активные точки — для выбора клиентом при оформлении заказа. */
    public List<PickupPointResponse> listActive() {
        return repo.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    /** Все точки (включая неактивные) — для админ-панели. */
    @PreAuthorize("hasRole('ADMIN')")
    public List<PickupPointResponse> listAll() {
        return repo.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    /** Сущность точки по id — используется при оформлении заказа для снимка адреса. */
    public PickupPoint getEntity(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Точка самовывоза не найдена: " + id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PickupPointResponse create(PickupPointRequest req) {
        PickupPoint p = new PickupPoint();
        p.setName(req.getName().trim());
        p.setAddress(req.getAddress().trim());
        p.setMetro(trimOrNull(req.getMetro()));
        p.setActive(req.getActive() == null || req.getActive());   // по умолчанию активна
        PickupPoint saved = repo.save(p);
        log.info("ACTION=PICKUP_POINT_CREATED id={} name={}", saved.getId(), saved.getName());
        return toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PickupPointResponse update(Long id, PickupPointRequest req) {
        PickupPoint p = getEntity(id);
        p.setName(req.getName().trim());
        p.setAddress(req.getAddress().trim());
        p.setMetro(trimOrNull(req.getMetro()));
        if (req.getActive() != null) p.setActive(req.getActive());
        log.info("ACTION=PICKUP_POINT_UPDATED id={} name={} active={}", id, p.getName(), p.isActive());
        return toResponse(repo.save(p));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new ResourceNotFoundException("Точка самовывоза не найдена: " + id);
        }
        repo.deleteById(id);
        log.info("ACTION=PICKUP_POINT_DELETED id={}", id);
    }

    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private PickupPointResponse toResponse(PickupPoint p) {
        return new PickupPointResponse(p.getId(), p.getName(), p.getAddress(), p.getMetro(), p.isActive());
    }
}
