package com.wms.inventory.repository;

import com.wms.inventory.entity.StocktakeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, Long> {

    @Query("SELECT COUNT(l) FROM StocktakeLine l WHERE l.stocktakeHeader.id = :headerId")
    long countByHeaderId(@Param("headerId") Long headerId);

    @Query("SELECT COUNT(l) FROM StocktakeLine l WHERE l.stocktakeHeader.id = :headerId AND l.isCounted = true")
    long countCountedByHeaderId(@Param("headerId") Long headerId);
}
