package io.github.janshuz.movie;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MovieRecommenderFlowTest {

    @Autowired
    private MockMvc http;

    @Test
    void recommendationsUseRatingsAndExcludeRatedMovies() throws Exception {
        http.perform(get("/api/recommendations").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].source").value("collaborative"))
                .andExpect(jsonPath("$[*].movie.id", not(hasItem(8))));

        http.perform(put("/api/users/1/ratings/11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 4}"))
                .andExpect(status().isNoContent());
        http.perform(put("/api/users/1/ratings/11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 2}"))
                .andExpect(status().isNoContent());

        http.perform(get("/api/movies").param("q", "地平线"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ratingCount").value(2))
                .andExpect(jsonPath("$[0].averageRating").value(3.5));
        http.perform(get("/api/recommendations").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].movie.id", not(hasItem(11))));
    }

    @Test
    void sparseUsersAndVisitorsReceivePopularMovies() throws Exception {
        http.perform(get("/api/recommendations").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("popular"));
        http.perform(get("/api/recommendations").param("userId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].source", everyItem(is("popular"))))
                .andExpect(jsonPath("$[*].movie.id", not(hasItem(7))));
    }

    @Test
    void invalidRequestsReturnClientErrors() throws Exception {
        http.perform(get("/api/recommendations").param("limit", "51"))
                .andExpect(status().isBadRequest());
        http.perform(get("/api/recommendations").param("userId", "999"))
                .andExpect(status().isNotFound());
        http.perform(put("/api/users/1/ratings/11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 4.2}"))
                .andExpect(status().isBadRequest());
        http.perform(put("/api/users/1/ratings/11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": \"5\"}"))
                .andExpect(status().isBadRequest());
        http.perform(put("/api/users/1/ratings/11")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 0}"))
                .andExpect(status().isBadRequest());
        http.perform(put("/api/users/1/ratings/999")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"score\": 4}"))
                .andExpect(status().isNotFound());
    }
}
