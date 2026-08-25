package com.spedite.logistics.entity;

import com.spedite.logistics.dto.Address;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "consignee_master")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsigneeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long consigneeId;

    private String name;
    private String gstNumber;
    private String contactNumber;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Address address;
}
