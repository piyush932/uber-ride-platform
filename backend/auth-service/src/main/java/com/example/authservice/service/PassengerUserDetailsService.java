package com.example.authservice.service;

import com.example.authservice.repository.PassengerRepository;
import com.example.entityservice.models.Passenger;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PassengerUserDetailsService implements UserDetailsService {

    private final PassengerRepository passengerRepository;

    public PassengerUserDetailsService(PassengerRepository passengerRepository) {
        this.passengerRepository = passengerRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Passenger passenger = passengerRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("No passenger with email " + email));

        return User.withUsername(passenger.getEmail())
            .password(passenger.getPassword())
            .roles("PASSENGER")
            .build();
    }
}
