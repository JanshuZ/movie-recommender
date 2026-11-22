"use strict";

const { createApp, nextTick, onMounted, ref } = Vue;

async function request(path, options) {
  const response = await fetch(path, options);
  if (response.ok) {
    return response.status === 204 ? undefined : response.json();
  }

  const problem = await response.json().catch(() => null);
  throw new Error(problem?.detail || problem?.message || `请求失败（HTTP ${response.status}）`);
}

createApp({
  setup() {
    const users = ref([]);
    const activeUser = ref("");
    const usersError = ref("");
    const query = ref("");
    const movies = ref([]);
    const catalogLoading = ref(true);
    const catalogError = ref("");
    const recommendations = ref([]);
    const recommendationLoading = ref(true);
    const recommendationError = ref("");
    const detailsDialog = ref(null);
    const selectedMovie = ref(null);
    const selectedRecommendation = ref(null);
    const rating = ref(null);
    const saving = ref(false);
    const ratingError = ref("");
    const ratingMessage = ref("");
    let catalogRequest = 0;
    let recommendationRequest = 0;
    let submittedQuery = "";
    let detailTrigger = null;

    async function loadUsers() {
      usersError.value = "";
      try {
        users.value = await request("/api/users");
      } catch {
        usersError.value = "无法读取演示用户。";
      }
    }

    async function loadMovies() {
      const currentRequest = ++catalogRequest;
      catalogLoading.value = true;
      catalogError.value = "";
      const params = new URLSearchParams({ q: submittedQuery });

      try {
        const result = await request(`/api/movies?${params}`);
        if (currentRequest !== catalogRequest) return false;
        movies.value = result;
        if (selectedMovie.value) {
          selectedMovie.value = result.find(movie => movie.id === selectedMovie.value.id)
            ?? selectedMovie.value;
        }
        return true;
      } catch {
        if (currentRequest === catalogRequest) catalogError.value = "无法读取电影目录。";
        return false;
      } finally {
        if (currentRequest === catalogRequest) catalogLoading.value = false;
      }
    }

    function searchMovies() {
      submittedQuery = query.value.trim();
      return loadMovies();
    }

    async function loadRecommendations() {
      const currentRequest = ++recommendationRequest;
      recommendationLoading.value = true;
      recommendationError.value = "";
      const params = new URLSearchParams({ limit: "6" });
      if (activeUser.value) params.set("userId", activeUser.value);

      try {
        const result = await request(`/api/recommendations?${params}`);
        if (currentRequest !== recommendationRequest) return false;
        recommendations.value = result;
        return true;
      } catch {
        if (currentRequest === recommendationRequest) recommendationError.value = "无法生成推荐片单。";
        return false;
      } finally {
        if (currentRequest === recommendationRequest) recommendationLoading.value = false;
      }
    }

    function changeUser() {
      loadRecommendations();
    }

    function formatScore(score) {
      return Number(score).toFixed(1);
    }

    function recommendationReason(item) {
      if (item.source === "collaborative") {
        return `预测 ${formatScore(item.score)} 分 · ${item.neighborCount} 位相似用户`;
      }
      return "热门推荐";
    }

    async function openDetails(trigger, movie, recommendation = null) {
      detailTrigger = trigger;
      selectedMovie.value = movie;
      selectedRecommendation.value = recommendation;
      rating.value = null;
      ratingError.value = "";
      ratingMessage.value = "";
      await nextTick();
      detailsDialog.value.showModal();
    }

    function closeDetails() {
      selectedMovie.value = null;
      selectedRecommendation.value = null;
      if (detailTrigger?.isConnected) {
        detailTrigger.focus();
      } else {
        document.querySelector("#recommendation-heading").focus();
      }
      detailTrigger = null;
    }

    async function saveRating() {
      if (!activeUser.value || !selectedMovie.value || !rating.value || saving.value) return;

      const { id: movieId, title } = selectedMovie.value;
      saving.value = true;
      ratingError.value = "";
      ratingMessage.value = "";
      try {
        await request(`/api/users/${encodeURIComponent(activeUser.value)}/ratings/${movieId}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ score: rating.value }),
        });
        const [catalogUpdated, recommendationsUpdated] = await Promise.all([
          loadMovies(),
          loadRecommendations(),
        ]);
        if (selectedMovie.value?.id === movieId) {
          selectedRecommendation.value = null;
          if (catalogUpdated && !movies.value.some(movie => movie.id === movieId)) {
            const params = new URLSearchParams({ q: title });
            const details = await request(`/api/movies?${params}`).catch(() => []);
            if (selectedMovie.value?.id === movieId) {
              selectedMovie.value = details.find(movie => movie.id === movieId) ?? selectedMovie.value;
            }
          }
          if (selectedMovie.value?.id !== movieId) return;
          if (!catalogUpdated) {
            ratingMessage.value = "评分已保存。电影目录暂未更新。";
          } else if (!recommendationsUpdated) {
            ratingMessage.value = "评分已保存。推荐片单暂未更新。";
          } else {
            ratingMessage.value = "评分已保存，推荐片单已更新。";
          }
        }
      } catch (error) {
        if (selectedMovie.value?.id === movieId) ratingError.value = `评分保存失败。${error.message}`;
      } finally {
        saving.value = false;
      }
    }

    onMounted(() => {
      loadUsers();
      loadMovies();
      loadRecommendations();
    });

    return {
      users, activeUser, usersError, query, movies, catalogLoading, catalogError,
      recommendations, recommendationLoading, recommendationError, detailsDialog,
      selectedMovie, selectedRecommendation, rating, saving, ratingError, ratingMessage,
      loadUsers, loadMovies, searchMovies, loadRecommendations, changeUser, formatScore,
      recommendationReason, openDetails, closeDetails, saveRating,
    };
  },
}).mount("#app");
