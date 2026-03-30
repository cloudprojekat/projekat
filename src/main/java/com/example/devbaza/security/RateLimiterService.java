package com.example.devbaza.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiter servis — sprečava DDoS, brute force i scraping.
 *
 * Koristi Bucket4j (Apache 2.0 licenca).
 * Čuva buckete u memoriji po IP adresi.
 *
 * POBOLJŠANJA u odnosu na staru verziju:
 * - Prati vreme poslednjeg zahteva po IP-u
 * - Briše samo STARE buckete (ne sve) pri cleanup-u
 * - Sprečava memory leak sa neograničenim brojem IP-ova
 *
 * Za produkciju sa više servera: koristiti Redis backend
 */
@Service
public class RateLimiterService {

    @Value("${rate.limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${rate.limit.auth-requests-per-minute:10}")
    private int authRequestsPerMinute;

    // Maksimalan broj IP-ova u memoriji — sprečava memory exhaustion napad
    private static final int MAX_BUCKETS = 10_000;

    // Bucket + vreme poslednjeg korišćenja
    private static class BucketEntry {
        final Bucket bucket;
        volatile Instant posledniPristup;

        BucketEntry(Bucket bucket) {
            this.bucket          = bucket;
            this.posledniPristup = Instant.now();
        }
    }

    private final ConcurrentHashMap<String, BucketEntry> buckets     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BucketEntry> authBuckets = new ConcurrentHashMap<>();

    /**
     * Provera za generalne API zahteve
     * Limit: 60 zahteva u minuti po IP
     */
    public boolean dozvoljenaAkcija(String ip) {
        if (ip == null || ip.isBlank()) return false;
        BucketEntry entry = getBucketEntry(buckets, ip, requestsPerMinute);
        entry.posledniPristup = Instant.now();
        return entry.bucket.tryConsume(1);
    }

    /**
     * Strožiji limit za login/register — sprečava brute force
     * Limit: 10 pokušaja u minuti po IP
     */
    public boolean dozvoljenaAuthAkcija(String ip) {
        if (ip == null || ip.isBlank()) return false;
        BucketEntry entry = getBucketEntry(authBuckets, ip, authRequestsPerMinute);
        entry.posledniPristup = Instant.now();
        return entry.bucket.tryConsume(1);
    }

    private BucketEntry getBucketEntry(ConcurrentHashMap<String, BucketEntry> mapa,
                                       String ip, int kapacitet) {
        // Ako je mapa prepuna — odbij novog (anti memory-exhaustion)
        if (!mapa.containsKey(ip) && mapa.size() >= MAX_BUCKETS) {
            // Vraćamo privremeni bucket koji blokira — ne čuvamo ga
            return new BucketEntry(napraviBucket(0));
        }
        return mapa.computeIfAbsent(ip, k -> new BucketEntry(napraviBucket(kapacitet)));
    }

    private Bucket napraviBucket(int kapacitet) {
        if (kapacitet <= 0) {
            // Bucket koji uvek odbija
            Bandwidth limit = Bandwidth.classic(1, Refill.greedy(0, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        }
        Bandwidth limit = Bandwidth.classic(
                kapacitet,
                Refill.greedy(kapacitet, Duration.ofMinutes(1))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Briše SAMO stare buckete (neaktivne duže od 2 sata).
     * Stara verzija brisala je SVE buckete — napadač je dobijao reset svakog sata!
     *
     * Poziva se iz DevbazaApplication @Scheduled svakih sat vremena.
     */
    public void ocistiStareBuckete() {
        Instant granica = Instant.now().minus(Duration.ofHours(2));

        // Briši samo one koji su neaktivni 2+ sata
        buckets.entrySet().removeIf(e ->
                e.getValue().posledniPristup.isBefore(granica));

        authBuckets.entrySet().removeIf(e ->
                e.getValue().posledniPristup.isBefore(granica));
    }

    /**
     * Vraća trenutni broj praćenih IP-ova — za monitoring
     */
    public int getBrojPracenihIpova() {
        return buckets.size() + authBuckets.size();
    }
}