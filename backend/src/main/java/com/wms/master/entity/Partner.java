package com.wms.master.entity;

import com.wms.shared.entity.MasterBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Partner extends MasterBaseEntity {

    @Column(name = "partner_code", nullable = false, unique = true, length = 50)
    private String partnerCode;

    @Column(name = "partner_name", nullable = false, length = 200)
    private String partnerName;

    @Column(name = "partner_name_kana", length = 200)
    private String partnerNameKana;

    // TODO: #70 @Enumerated(EnumType.STRING) + Java Enum への移行を検討
    //       現フェーズは JPQL の IN 句との相性を考慮して String のまま維持
    @Column(name = "partner_type", nullable = false, length = 20)
    private String partnerType;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "contact_person", length = 100)
    private String contactPerson;

    @Column(name = "email", length = 200)
    private String email;
}
