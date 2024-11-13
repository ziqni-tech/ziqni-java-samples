package com.ziqni.admin;

import com.ziqni.admin.sdk.ZiqniAdminApiFactory;
import com.ziqni.admin.sdk.configuration.AdminApiClientConfigBuilder;
import com.ziqni.admin.sdk.model.*;
import com.ziqni.util.GlobalExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class ZiqniAdminSample {

    private static final Logger logger = LoggerFactory.getLogger(ZiqniAdminSample.class);

    public ZiqniAdminSample(String apiKey, String spaceName) throws Exception {

        logger.info("Running Admin Sample...");

        var configuration = AdminApiClientConfigBuilder.build();
        configuration.setApiKey(true);
        configuration.setAdminClientIdentityApiKey(apiKey);
        configuration.setAdminClientIdentityRealm(spaceName);
        configuration.setAdminClientIdentityProjectUrl(spaceName+".ziqni.app");

        var ziqniAdminApiFactory = new ZiqniAdminApiFactory(configuration);

        logger.info("Launched compute engine app for project [{}] and user [{}]", configuration.getAdminClientIdentityProjectUrl(), configuration.getAdminClientIdentityUser());
        Thread.setDefaultUncaughtExceptionHandler(new GlobalExceptionHandler());
        ziqniAdminApiFactory.initialise();

        if(configuration.isWebsocket()) {
            while (ziqniAdminApiFactory.getStreamingClient() == null) {
                Thread.sleep(500);
                logger.info("+++ Initializing the streaming admin api client");
            }

            final AtomicInteger counter = new AtomicInteger(0);
            final var started = ziqniAdminApiFactory.getStreamingClient().start();
            while (!ziqniAdminApiFactory.getStreamingClient().isConnected()) {
                Thread.sleep(500);
                logger.info("+++ Waiting for the streaming client to start [{}]",counter.incrementAndGet());
            }
            logger.info("+++ Started the streaming client");
        }
        else
            throw new RuntimeException("+++ Only socket based communications is used for this platform as REST is to in-efficient for intensive data processing");

        logger.info("+++ Admin API connected");

        final var members = getSomeMembers(ziqniAdminApiFactory);


        final var eventsCreated = members.thenAccept( memberList -> {
            if(!memberList.isEmpty()){
                memberList.forEach(member ->
                        registerMemberEvent(ziqniAdminApiFactory, member)
                );
            }
        });

    }

    public CompletableFuture<List<Member>> getSomeMembers(ZiqniAdminApiFactory ziqniAdminApiFactory){

        logger.info("+++ Getting members...");

        final var request =ziqniAdminApiFactory.getMembersApi().getMembersByQuery( // Get members by query
                new QueryRequest().addMultiFieldsItem( // must contain using fuzziness if it is text, if it is a key field exact matches
                        new QueryMultipleFields()
                                .addQueryFieldsItem("name") // Search by name
                                .queryValue("bob") // Find members with name John Doe
                ).skip(0).limit(3) // Limit to 3 members
                        .addSortByItem(new QuerySortBy().queryField("_score").order(SortOrder.DESC)) // Sort by score, ie most relevant first
        );

        return request.handle( (response, throwable) -> {

            if(throwable != null){
                logger.error("+++ Error getting members: "+throwable.getMessage());
                throw new RuntimeException("+++ Error getting members: "+throwable.getMessage());
            }

            if(response.getMeta().getErrorCount() > 0){
                logger.error("+++ Error getting members: "+response.getErrors());
                throw new RuntimeException("+++ Error getting members: "+response.getErrors());
            }

            if(response.getMeta().getResultCount() > 0){
                logger.info("+++ Members found: "+response.getResults().size());
                response.getResults().forEach(member -> {
                    logger.info("+++ Member: "+member.getName());
                });
            }

            return response.getResults();
        });
    }

    public CompletableFuture<ModelApiResponse> registerMemberEvent(ZiqniAdminApiFactory ziqniAdminApiFactory, Member member){

        logger.info("+++ Registering event for member: "+member.getName());

        // It helps to think of the data in the context of sentence, like: "John Doe bought 5 apples for $1 at 3pm the apples were fresh"
        CreateEventRequest createEventRequest = new CreateEventRequest()
                .memberRefId(member.getMemberRefId()) // who <> YOUR unique id for this member
                .action("buy") // how
                .entityRefId("apples") // what
                .sourceValue(5.0) // how many
                .transactionTimestamp(OffsetDateTime.now()) // when
                .putCustomFieldsItem("condition", "fresh") // additional data
                .eventRefId("1234") // Your unique id for this event
                .unitOfMeasure("other")// can be currency, calories, etc
                ;

        return ziqniAdminApiFactory.getEventsApi().createEvents(List.of(createEventRequest))
                .handle((response, throwable) -> {

                    if(throwable != null){
                        logger.error("+++ Error registering event: "+throwable.getMessage());
                        throw new RuntimeException("+++ Error registering event: "+throwable.getMessage());
                    }

                    if(response.getMeta().getErrorCount() > 0){
                        logger.error("+++ Error registering event: "+response.getErrors());
                        throw new RuntimeException("+++ Error registering event: "+response.getErrors());
                    }

                    if(response.getMeta().getResultCount() > 0){
                        logger.info("+++ Event registered: "+response.getResults().get(0).getId());
                    }

                    return response;
                })
                ;

    }
}
