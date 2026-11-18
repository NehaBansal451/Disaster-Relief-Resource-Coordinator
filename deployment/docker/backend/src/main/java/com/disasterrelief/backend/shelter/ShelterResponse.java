package com.disasterrelief.backend.shelter;

/**
 * freeBeds = max(0, capacity - occupancy); overcrowded = occupancy > capacity. distanceKm is set only for the
 * alternatives endpoint. Numbers are always people / places, never an unexplained percentage.
 */
public record ShelterResponse(long id, String code, String name, String address, String district, String state,
                              int capacity, int occupancy, int freeBeds, boolean overcrowded, String facilities,
                              boolean accessible, String status, Double latitude, Double longitude, boolean demo,
                              Double distanceKm) {
}
