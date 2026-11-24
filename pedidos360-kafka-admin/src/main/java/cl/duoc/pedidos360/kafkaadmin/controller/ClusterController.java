package cl.duoc.pedidos360.kafkaadmin.controller;

import cl.duoc.pedidos360.kafkaadmin.dto.Dtos.ClusterResponse;
import cl.duoc.pedidos360.kafkaadmin.service.KafkaAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Clúster")
@RestController
@RequestMapping("/api/kafka-admin/cluster")
public class ClusterController {

    private final KafkaAdminService adminService;

    public ClusterController(KafkaAdminService adminService) {
        this.adminService = adminService;
    }

    @Operation(summary = "Brokers del clúster y cuál es el controlador")
    @GetMapping
    public ClusterResponse cluster() {
        return adminService.cluster();
    }
}
