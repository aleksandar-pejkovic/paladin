package com.samsara.paladin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.samsara.paladin.dto.HeroDto;
import com.samsara.paladin.service.hero.HeroServiceImpl;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/heroes")
@Validated
public class HeroController {

    private static final int MIN_LEVEL = 0;

    private static final int MAX_LEVEL = 80;

    private final HeroServiceImpl heroService;

    @Autowired
    public HeroController(HeroServiceImpl heroService) {
        this.heroService = heroService;
    }

    @PostMapping("/create")
    public ResponseEntity<HeroDto> createHero(@Valid @RequestBody HeroDto heroDto) {
        HeroDto heroResponse = heroService.createHero(heroDto);
        return new ResponseEntity<>(heroResponse, HttpStatus.CREATED);
    }

    @PutMapping("/update")
    public ResponseEntity<HeroDto> updateHero(@Valid @RequestBody HeroDto heroDto) {
        HeroDto heroResponse = heroService.updateHero(heroDto);
        return new ResponseEntity<>(heroResponse, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{name}")
    public String removeHero(@PathVariable @NotBlank String name) {
        heroService.deleteHero(name);
        return "Hero '" + name + "' deleted!";
    }

    @GetMapping("/all")
    public List<HeroDto> fetchAllHeroes() {
        return heroService.loadAllHeroes();
    }

    @GetMapping("/name/{name}")
    public HeroDto fetchHeroByName(@PathVariable @NotBlank String name) {
        return heroService.loadHeroByName(name);
    }

    @GetMapping("/username/{username}")
    public List<HeroDto> fetchHeroesByUser(@PathVariable @NotBlank String username) {
        return heroService.loadHeroesByUser(username);
    }

    @GetMapping("/level/{level}")
    public List<HeroDto> fetchHeroesByLevel(@PathVariable @Min(MIN_LEVEL) @Max(MAX_LEVEL) Integer level) {
        return heroService.loadHeroesByLevel(level);
    }

    @GetMapping("/min-level/{level}")
    public List<HeroDto> fetchHeroesByMinLevel(@PathVariable @Min(MIN_LEVEL) @Max(MAX_LEVEL) Integer level) {
        return heroService.loadHeroesByMinLevel(level);
    }

    @GetMapping("/max-level/{level}")
    public List<HeroDto> fetchHeroesByMaxLevel(@PathVariable @Min(MIN_LEVEL) @Max(MAX_LEVEL) Integer level) {
        return heroService.loadHeroesByMaxLevel(level);
    }

    @GetMapping("/best")
    public List<HeroDto> fetchBestHeroes() {
        return heroService.loadBest10HeroesByLevel();
    }

    @GetMapping("/newest")
    public List<HeroDto> fetchLastAddedHeroes() {
        return heroService.loadLast10AddedHeroes();
    }
}
