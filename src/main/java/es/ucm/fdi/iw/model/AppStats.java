package es.ucm.fdi.iw.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AppStats {
    private long publicRooms;
    private long privateRooms;
    private long totalUsers;
    private long todayWinners;

    public AppStats(long publicRooms, long privateRooms, long totalUsers, long todayWinners){
        this.publicRooms = publicRooms;
        this.privateRooms = privateRooms;
        this.totalUsers = totalUsers;
        this.todayWinners = todayWinners;
    }

}
