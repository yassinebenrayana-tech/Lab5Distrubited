package org.example.kitchen.repository;

import org.example.kitchen.model.KitchenTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KitchenTicketRepository extends JpaRepository<KitchenTicket, String> {
}
