package alloverdev.operation;

import java.util.Date;

public class Robbery {

    private String guard, thief;
    private Date startDate;
    private boolean alarm;
    private double stealedMoney;
    private boolean completed;

    public Robbery(String guard, String thief, Date startDate, double stealedMoney) {

        this.guard = guard;
        this.thief = thief;
        this.startDate = startDate;
        this.alarm = false;
        this.stealedMoney = stealedMoney;

    }

    public String getGuard() {
        return guard;
    }

    public void setGuard(String guard) {
        this.guard = guard;
    }

    public String getThief() {
        return thief;
    }

    public void setThief(String thief) {
        this.thief = thief;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public boolean isAlarm() {
        return alarm;
    }

    public void setAlarm(boolean alarm) {
        this.alarm = alarm;
    }

    public double getStealedMoney() {
        return stealedMoney;
    }

    public void setStealedMoney(double stealedMoney) {
        this.stealedMoney = stealedMoney;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean checkAlarm() {
        if (!isCompleted()) {
            this.alarm = startDate.getTime() < new Date().getTime() - 5000;
            System.out.println("start date: " + startDate.getTime());
            System.out.println("new Date().getTime() - 5000: " + (new Date().getTime() - 5000));
            System.out.println("alarm: " + this.alarm);
        } else {
            this.alarm = false;
        }
        return this.alarm;
    }
}
