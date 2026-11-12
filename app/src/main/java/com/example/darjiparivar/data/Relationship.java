package com.example.darjiparivar.data;

/** How the signed-in user is connected to another member. */
public class Relationship {
    public boolean shortlisted;
    public boolean interestSent;
    /** They sent an interest that the user hasn't answered yet. */
    public boolean interestReceived;
    /** An interest was accepted by either side, so they can chat. */
    public boolean connected;
    public boolean blocked;
}
