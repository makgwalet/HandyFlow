package za.co.handyflow.platform.billing.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "module_catalogue")
@Getter
@NoArgsConstructor
public class ModuleCatalogue {

    @Id UUID id;
    @Column(name = "key", unique = true) String key;
    String name;
    String description;
    @Column(name = "monthly_price") BigDecimal monthlyPrice;
    String currency = "ZAR";
    String icon;
    String category;
    boolean active = true;
    @Column(name = "sort_order") int sortOrder;
    @Column(name = "created_at") Instant createdAt;
}