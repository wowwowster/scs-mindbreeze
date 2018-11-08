package com.sword.gsa.spis.scs.exceptions;

public class DoNotExplore extends Exception {
    
    private static final long serialVersionUID = -9204152149972551939L;
    
    private String detailMessage;
    
    public DoNotExplore(String cause) {
    	detailMessage = cause;
    }
    
    public String toString() {
        return "Cause of not exploring  : " + detailMessage;        		
    }

    @Override
	public String getMessage() {
		return detailMessage;
	}
    
}
