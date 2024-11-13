package com.ziqni.member;

import com.google.common.eventbus.Subscribe;
import com.ziqni.member.sdk.SampleApp;
import com.ziqni.member.sdk.ZiqniMemberApiFactory;
import com.ziqni.member.sdk.configuration.MemberApiClientConfigBuilder;
import com.ziqni.member.sdk.context.WSClientConnected;
import com.ziqni.member.sdk.context.WSClientConnecting;
import com.ziqni.member.sdk.context.WSClientDisconnected;
import com.ziqni.member.sdk.context.WSClientSevereFailure;
import com.ziqni.member.sdk.model.*;
import com.ziqni.util.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ZiqniMemberSample {

    private static final Logger logger = LoggerFactory.getLogger(ZiqniMemberSample.class);

    private static ZiqniMemberApiFactory ziqniMemberApiFactory;
    private static Map<String, Map<Integer, Double>> prevScores = new HashMap<>();
    private static final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

    public ZiqniMemberSample(String apiKey, String spaceName, String memberReferenceId) throws Exception {
        logger.info("Running Member Sample...");

        final var memberAccessToken = MemberAccessTokenService.getToken(memberReferenceId, apiKey);


        var configuration = MemberApiClientConfigBuilder.build();
        configuration.setIdentityAuthorizationToken(memberAccessToken.getData().getJwtToken());


        ziqniMemberApiFactory = new ZiqniMemberApiFactory(configuration);

        logger.info("+++ Member API connected");
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        ziqniMemberApiFactory.initialise();


        while (ziqniMemberApiFactory.getStreamingClient() == null) {
            Thread.sleep(500);
            logger.info("+++ Initializing the streaming member api client");
        }

        final AtomicInteger counter = new AtomicInteger(0);
        final var started = ziqniMemberApiFactory.getStreamingClient().start();
        while (!ziqniMemberApiFactory.getStreamingClient().isConnected()) {
            Thread.sleep(500);
            logger.info("+++ Waiting for the streaming client to start [{}]",counter.incrementAndGet());
        }

        logger.info("+++ Started the streaming client");

        ziqniMemberApiFactory = ziqniMemberApiFactory;

        logger.info("+++ Member API connected");
    }

    public void launch(String[] args) throws Exception {

        ziqniMemberApiFactory = new ZiqniMemberApiFactory(MemberApiClientConfigBuilder.build());

        ziqniMemberApiFactory.getCallbacksApi().leaderboardUpdateHandler(
                (stompHeaders, leaderboard) -> {
                    logger.info("Leaderboard {} - ", leaderboard.getId());
                    prevScores.putIfAbsent(leaderboard.getId(), new HashMap<>());
                    final Map<Integer, Double> map = prevScores.get(leaderboard.getId());

                    if(!Objects.requireNonNull(leaderboard.getLeaderboardEntries()).isEmpty()){
                        leaderboard.getLeaderboardEntries().stream()
                                .sorted(Comparator.comparing(LeaderboardEntry::getRank))
                                .forEach(leaderboardEntry -> {
                                    final var changed = !Objects.equals(map.getOrDefault(leaderboardEntry.getRank(), 0.0), leaderboardEntry.getScore())
                                            ? " *" : "";
                                    logger.info("-{} {}- {} {}", leaderboardEntry.getRank(), leaderboardEntry.getScore(), leaderboardEntry.getMembers().stream().map(LeaderboardMember::getName).collect(Collectors.toList()), changed);
                                })
                        ;

                        map.clear();
                        leaderboard.getLeaderboardEntries().forEach(leaderboardEntry -> {
                            map.put(leaderboardEntry.getRank(), leaderboardEntry.getScore());
                        });
                    }
                },
                (stompHeaders, e) -> {}
        );

        ziqniMemberApiFactory.initialise(() -> {
                    ziqniMemberApiFactory.getZiqniAdminEventBus().register(new SampleApp());
                    try {
                        return ziqniMemberApiFactory.getStreamingClient().start();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Failed to connect", throwable);
                    return null;
                })
                .thenAccept(started -> {
                    logger.error("Connection is {}", started == null || !started ? "NOT_STARTED" : "RUNNING");
                });
    }

    //////// ADMIN API CLIENT EVENTBUS ////////
    @Subscribe
    public void onWSClientConnected(WSClientConnected change) {
        if(change.getConnectedHeaders() == null)
            return;
        logger.info("WSClientConnected {}", change);
        this.onStart();
    }

    @Subscribe
    public void onWSClientConnecting(WSClientConnecting change) {
        logger.info("WSClientConnecting {}", change);
    }

    @Subscribe
    public void onWSClientDisconnected(WSClientDisconnected change){
        logger.info("WSClientDisconnected {}", change);
    }

    @Subscribe
    public void onWSClientSevereFailure(WSClientSevereFailure change){
        logger.info("WSClientSevereFailure {}", change);
    }

    private void handleResponse(AchievementResponse achievementResponse){

        if(achievementResponse.getData() != null){
            achievementResponse.getData().forEach(achievement -> {
                if(Objects.nonNull(achievement.getConstraints()) && achievement.getConstraints().contains("optinRequiredForEntrants")){
                    optIntoAchievement(achievement);
                }
            });
        }

        System.out.println(achievementResponse);
    }

    private void handleResponse(CompetitionResponse competitionResponse){

        if(competitionResponse.getData() != null){
            competitionResponse.getData().forEach(competition -> {
                if(Objects.nonNull(competition.getConstraints()) && competition.getConstraints().contains("optinRequiredForEntrants")){

                }
                else {

                }
            });
        }

        System.out.println(competitionResponse);

        competitionResponse.getData().stream().findFirst().ifPresent(this::getContests);
    }

    private void getContests(Competition competition){
        ziqniMemberApiFactory.getContestsApi().getContests(new ContestRequest().contestFilter(new ContestFilter()/*.competitionIds(List.of("V-UJyIcB2XLhi587MUz1"))*/))
                .thenAccept(contestResponse -> {
                    logger.info(contestResponse.getData().toString());
                    contestResponse.getData().stream().findFirst().ifPresent(contest -> {
                        this.getRewards(contest);
                        this.subscribeToLeaderboard(contest);
                    });
                }).exceptionally(throwable -> {
                    logger.error("Failed to get contests for competition {}", competition, throwable);
                    return null;
                });
    }

    private void getRewards(Contest contest) {
        ziqniMemberApiFactory.getRewardsApi().getRewards(new RewardRequest()
                        //.addSortByItem(new QuerySortBy().queryField("created").order(SortOrder.ASC))
                        .skip(0)
                        .limit(10)
                        .currencyKey("GBP")
                        .addEntityFilterItem(
                                new EntityFilter()
                                        .addEntityIdsItem(contest.getId())
                                        .entityType(contest.getClass().getSimpleName())
                        )
                )
                .thenAccept(rewardResponse ->
                        logger.info(rewardResponse.getData().toString())
                )
                .exceptionally(throwable -> {
                    logger.error("Failed to get rerwards for contest {}", contest, throwable);
                    return null;
                });
    }

    private void getAwards() {
        ziqniMemberApiFactory.getAwardsApi().getAwards(new AwardRequest()
                        //.addSortByItem(new QuerySortBy().queryField("created").order(SortOrder.ASC))
                        .currencyKey("GBP")
                        .awardFilter(new AwardFilter().skip(0).limit(10))
                )
                .thenAccept(awardResponse ->
                        logger.info(awardResponse.getData().toString())
                )
                .exceptionally(throwable -> {
                    logger.error("Failed to get awards for contest ", throwable);
                    return null;
                });
    }

    private void subscribeToLeaderboard(Contest contest){
        ziqniMemberApiFactory.getLeaderboardApi().subscribeToLeaderboard(new LeaderboardSubscriptionRequest()
                .leaderboardFilter(new LeaderboardFilter()
                        .ranksBelowToInclude(5)
                        .ranksAboveToInclude(5)
                        .topRanksToInclude(10)
                )
                .action(LeaderboardSubscriptionRequest.ActionEnum.SUBSCRIBE)
                .entityId(contest.getId())
        ).thenAccept(leaderboardsResponse -> {
            logger.info(leaderboardsResponse.toString());
        }).exceptionally(throwable -> {
            logger.error("Failed to subscribe to entity changes for  {}", LeaderboardSubscriptionRequest.class.getSimpleName(), throwable);
            return null;
        });;
    }

    private void optIntoAchievement(Achievement achievement){
        ziqniMemberApiFactory.getOptInApi().manageOptin(new ManageOptinRequest()
                .action(OptinAction.JOIN)
                .entityId(achievement.getId())
                .entityType(Achievement.class.getSimpleName())
        ).thenAccept(optInResponse -> {
            logger.info(optInResponse.getData().toString());
        }).exceptionally(throwable -> {
            logger.error("{} Failed to opt-in to {} [{}]", Achievement.class.getSimpleName(), achievement.getId(), achievement.getName(), throwable);
            return null;
        });
    }

    private void onStart() {

        subscribeToCallbacks();

        if(!ziqniMemberApiFactory.getStreamingClient().isConnected()) {
            ziqniMemberApiFactory.getStreamingClient().stop();
            timer.shutdown();
            throw new RuntimeException("Not connected");
        }

        ziqniMemberApiFactory.getCallbacksApi()
                .listCallbacks()
                .thenApply(response -> {
                    logger.info(response.toString());
                    return response;
                })
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });

        ziqniMemberApiFactory.getMembersApi()
                .getMember(new MemberRequest().addIncludeFieldsItem(Member.JSON_PROPERTY_MEMBER_REF_ID))
                .thenApply(memberResponse -> {
                    logger.info(memberResponse.toString());
                    return memberResponse;
                })
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });

        ziqniMemberApiFactory.getAwardsApi()
                .getAwards(new AwardRequest().currencyKey("GBP").awardFilter(
                                new AwardFilter()
                                        .limit(5)
                                        .statusCode(new NumberRange().moreThan(16L).lessThan(60L))
                        )
                )
                .thenAccept(awardResponse ->
                        logger.info(awardResponse.toString())
                )
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });

        ziqniMemberApiFactory.getAchievementsApi()
                .getAchievements(new AchievementRequest().achievementFilter(new AchievementFilter().statusCode(new NumberRange().moreThan(20L).lessThan(30L))))
                .thenAccept(this::handleResponse)
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });

        ziqniMemberApiFactory.getCompetitionsApi()
                .getCompetitions(new CompetitionRequest()
                        .competitionFilter(new CompetitionFilter()
                                .statusCode(new NumberRange().moreThan(20L).lessThan(30L))
                        ).languageKey("de")
                )
                .thenAccept(this::handleResponse)
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });

        ziqniMemberApiFactory.getOptInApi()
                .optInStates(new OptInStatesRequest().optinStatesFilter(
                        new OptinStatesFilter().addEntityTypesItem(EntityType.ACHIEVEMENT))
                )
                .thenAccept(response -> {
                    logger.info(response.toString());
                })
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });

        ziqniMemberApiFactory.getGraphsApi()
                .getGraph(
                        new EntityGraphRequest()
                                .entityType(EntityType.ACHIEVEMENT)
                                .addIdsItem("wr47SoYB4W1yU_TfNeYL")
//                                .addIdsItem("oLOWY4YBF0c3Crf1gj7J")
//                                .addIncludesItem("description")
//                                .addIncludesItem("scheduling")
                )
                .thenAccept(response -> {
                    logger.info(response.toString());
                })
                .exceptionally(throwable -> {
                    logger.error("Fail",throwable);
                    return null;
                });
    }

    private void subscribeToCallbacks(){

        ziqniMemberApiFactory.getCallbacksApi().entityChangedHandler(
                ((stompHeaders, entityChanged) -> {
                    logger.info(entityChanged.toString());
                }),
                (stompHeaders, error) ->
                        logger.info(error.toString())
        );

        ziqniMemberApiFactory.getCallbacksApi().entityStateChangedHandler(
                ((stompHeaders, entityStateChanged) ->{
                    logger.info(entityStateChanged.toString());
                }),
                (stompHeaders, error) ->
                        logger.info(error.toString())
        );

        ziqniMemberApiFactory.getCallbacksApi().optinStatusHandler(
                ((stompHeaders, optinStatus) ->{
                    logger.info(optinStatus.toString());
                }),
                (stompHeaders, error) ->
                        logger.info(error.toString())
        );

        ziqniMemberApiFactory.getCallbacksApi().notificationHandler(
                ((stompHeaders, message) -> {
                    logger.info(message.toString());
                }),
                (stompHeaders, error) ->
                        logger.info(error.toString())
        );

        ziqniMemberApiFactory.getCallbacksApi().leaderboardUpdateHandler(
                ((stompHeaders, message) -> {
                    logger.info(message.toString());
                }),
                (stompHeaders, error) ->
                        logger.info(error.toString())
        );
    }
}
