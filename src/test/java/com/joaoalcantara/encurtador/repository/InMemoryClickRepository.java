package com.joaoalcantara.encurtador.repository;

import com.joaoalcantara.encurtador.domain.DailyClicks;
import com.joaoalcantara.encurtador.domain.ReferrerClicks;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ClickRepository em memoria, usado apenas nos testes.
 *
 * As agregacoes que no adaptador real sao SQL aparecem aqui reescritas em Java.
 * Nao e duplicacao a toa: o valor e poder testar a montagem das estatisticas sem
 * banco. Quem confere se o SQL de verdade produz os mesmos numeros e o teste de
 * integracao da Etapa 7 -- por isso essas duas implementacoes precisam ficar sob
 * o mesmo contrato.
 */
public class InMemoryClickRepository implements ClickRepository {

    private record StoredClick(Long linkId, Instant clickedAt, String referrer, String userAgent, String ipHash) {
    }

    private final List<StoredClick> clicks = new ArrayList<>();

    @Override
    public void save(Long linkId, Instant clickedAt, String referrer, String userAgent, String ipHash) {
        clicks.add(new StoredClick(linkId, clickedAt, referrer, userAgent, ipHash));
    }

    private List<StoredClick> of(Long linkId) {
        return clicks.stream().filter(click -> java.util.Objects.equals(click.linkId(), linkId)).toList();
    }

    /** Acessores usados so pelos testes, para inspecionar o que foi gravado. */
    public List<String> storedIpHashes() {
        return clicks.stream().map(StoredClick::ipHash).toList();
    }

    public List<String> storedUserAgents() {
        return clicks.stream().map(StoredClick::userAgent).toList();
    }

    @Override
    public long countByLinkId(Long linkId) {
        return of(linkId).size();
    }

    @Override
    public long countDistinctVisitorsByLinkId(Long linkId) {
        return of(linkId).stream()
                .map(StoredClick::ipHash)
                .filter(hash -> hash != null)
                .distinct()
                .count();
    }

    @Override
    public Instant findLastClickAt(Long linkId) {
        return of(linkId).stream()
                .map(StoredClick::clickedAt)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    @Override
    public List<DailyClicks> countByDaySince(Long linkId, Instant since) {
        Map<java.time.LocalDate, Long> byDay = of(linkId).stream()
                .filter(click -> !click.clickedAt().isBefore(since))
                .collect(Collectors.groupingBy(
                        click -> click.clickedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        LinkedHashMap::new,
                        Collectors.counting()));

        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new DailyClicks(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<ReferrerClicks> findTopReferrers(Long linkId, int limit) {
        Map<String, Long> byReferrer = of(linkId).stream()
                .filter(click -> click.referrer() != null)
                .collect(Collectors.groupingBy(StoredClick::referrer, Collectors.counting()));

        return byReferrer.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new ReferrerClicks(entry.getKey(), entry.getValue()))
                .toList();
    }
}
