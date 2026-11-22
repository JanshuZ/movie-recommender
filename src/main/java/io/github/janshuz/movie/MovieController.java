package io.github.janshuz.movie;

import java.util.List;

import io.github.janshuz.movie.MovieStore.DemoUser;
import io.github.janshuz.movie.MovieStore.Movie;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class MovieController {

    record RatingRequest(Integer score) {}

    private final MovieService service;

    MovieController(MovieService service) {
        this.service = service;
    }

    @GetMapping("/movies")
    List<Movie> movies(@RequestParam(defaultValue = "") String q) {
        return service.movies(q);
    }

    @GetMapping("/users")
    List<DemoUser> users() {
        return service.users();
    }

    @GetMapping("/recommendations")
    List<MovieService.Result> recommendations(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "6") int limit) {
        return service.recommendations(userId, limit);
    }

    @PutMapping("/users/{userId}/ratings/{movieId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void rate(@PathVariable long userId, @PathVariable long movieId, @RequestBody RatingRequest rating) {
        service.rate(userId, movieId, rating.score());
    }
}
