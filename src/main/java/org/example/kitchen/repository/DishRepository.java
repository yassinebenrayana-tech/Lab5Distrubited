package org.example.kitchen.repository;

import org.example.kitchen.model.Dish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DishRepository extends JpaRepository<Dish, Integer> {
    // Houni lazem tkoun Integer mouch Long ---^
}

