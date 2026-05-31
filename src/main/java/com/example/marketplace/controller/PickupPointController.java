package com.example.marketplace.controller;

import com.example.marketplace.dto.request.PickupPointRequest;
import com.example.marketplace.dto.response.PickupPointResponse;
import com.example.marketplace.service.PickupPointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Точки самовывоза.
 *
 *   GET    /api/pickup-points              — активные точки (для оформления заказа клиентом)
 *   GET    /api/admin/pickup-points        — все точки (админ)
 *   POST   /api/admin/pickup-points        — создать точку (админ, 201)
 *   PUT    /api/admin/pickup-points/{id}   — изменить точку (админ)
 *   DELETE /api/admin/pickup-points/{id}   — удалить точку (админ, 204)
 *
 * Доступ к /api/admin/** ограничен ролью ADMIN в SecurityConfig.
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PickupPointController {

    private final PickupPointService service;

    @GetMapping("/api/pickup-points")
    public List<PickupPointResponse> listActive() {
        return service.listActive();
    }

    @GetMapping("/api/admin/pickup-points")
    public List<PickupPointResponse> listAll() {
        return service.listAll();
    }

    @PostMapping(value = "/api/admin/pickup-points", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PickupPointResponse create(@Valid @RequestBody PickupPointRequest req) {
        return service.create(req);
    }

    @PutMapping(value = "/api/admin/pickup-points/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PickupPointResponse update(@PathVariable Long id, @Valid @RequestBody PickupPointRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/api/admin/pickup-points/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
