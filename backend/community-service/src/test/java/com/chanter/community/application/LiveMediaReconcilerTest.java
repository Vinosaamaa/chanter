package com.chanter.community.application;

import static org.mockito.Mockito.*;

import io.livekit.server.RoomServiceClient;
import io.livekit.server.RoomService;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import livekit.LivekitModels;
import livekit.LivekitRoom;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import retrofit2.Call;
import retrofit2.Response;

class LiveMediaReconcilerTest {
    @Test void removesARestrictedParticipantWhileKeepingAuthorizedAudioAndRetriesAFailedRemoval() throws Exception {
        var service=mock(RoomService.class);
        var livekit=new RoomServiceClient(service,"test-key",LiveKitJoinGuardTest.SECRET);
        var access=mock(LiveMediaAccess.class);
        String room="voice-"+UUID.randomUUID();
        UUID allowed=UUID.randomUUID(),restricted=UUID.randomUUID();
        when(service.listRooms(any(),anyString())).thenAnswer(ignored -> response(LivekitRoom.ListRoomsResponse.newBuilder()
                .addRooms(LivekitModels.Room.newBuilder().setName(room)).build()));
        when(service.listParticipants(any(),anyString())).thenAnswer(ignored -> response(LivekitRoom.ListParticipantsResponse.newBuilder()
                .addParticipants(participant(allowed)).addParticipants(participant(restricted)).build()));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireAllowed(room,restricted);
        Call<Void> remove=mock(Call.class);
        when(remove.execute()).thenThrow(new IOException("Temporary media outage")).thenReturn(Response.success(null));
        when(service.removeParticipant(any(),anyString())).thenReturn(remove);
        var reconciler=new LiveMediaReconciler(livekit,access);
        reconciler.reconcile();
        reconciler.reconcile();
        verify(service,times(2)).removeParticipant(argThat(value -> value.getRoom().equals(room)
                && value.getIdentity().equals(restricted.toString())),anyString());
        verify(service,never()).removeParticipant(argThat(value -> value.getIdentity().equals(allowed.toString())),anyString());
    }

    @Test void anAuthorityOutageCannotKeepAnExistingParticipantAuthorized() throws Exception {
        var service=mock(RoomService.class);
        var livekit=new RoomServiceClient(service,"test-key",LiveKitJoinGuardTest.SECRET);
        var access=mock(LiveMediaAccess.class);
        String room="dm-call-"+UUID.randomUUID();
        UUID user=UUID.randomUUID();
        when(service.listRooms(any(),anyString())).thenAnswer(ignored -> response(LivekitRoom.ListRoomsResponse.newBuilder()
                .addRooms(LivekitModels.Room.newBuilder().setName(room)).build()));
        when(service.listParticipants(any(),anyString())).thenAnswer(ignored -> response(LivekitRoom.ListParticipantsResponse.newBuilder()
                .addParticipants(participant(user)).build()));
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(access).requireAllowed(room,user);
        when(service.removeParticipant(any(),anyString())).thenAnswer(ignored -> response(null));
        new LiveMediaReconciler(livekit,access).reconcile();
        verify(service).removeParticipant(argThat(value -> value.getRoom().equals(room)
                && value.getIdentity().equals(user.toString())),anyString());
    }

    private static LivekitModels.ParticipantInfo participant(UUID user) {
        return LivekitModels.ParticipantInfo.newBuilder().setIdentity(user.toString()).build();
    }
    @SuppressWarnings("unchecked") private static <T> Call<T> response(T body) throws IOException {
        Call<T> call=mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        return call;
    }
}
