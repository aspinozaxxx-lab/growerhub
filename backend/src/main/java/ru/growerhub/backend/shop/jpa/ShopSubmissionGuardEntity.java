package ru.growerhub.backend.shop.jpa;

import jakarta.persistence.*;

@Entity
@Table(name = "shop_submission_guard")
public class ShopSubmissionGuardEntity {
    @Id public Integer id;
}
