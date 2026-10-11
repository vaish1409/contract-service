package com.seventhray.contracts.repository;

import com.seventhray.contracts.model.Contract;
import com.seventhray.contracts.model.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    List<Contract> findByStatusOrderByCreatedAtDesc(ContractStatus status);
    List<Contract> findAllByOrderByCreatedAtDesc();
}
