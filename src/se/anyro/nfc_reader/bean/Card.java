package se.anyro.nfc_reader.bean;

import java.util.ArrayList;

import se.anyro.nfc_reader.SPEC;

public class Card extends Application {
	public final static Card Empty = new Card();
	
	private final ArrayList<Application> applications;
	
	public Card() {
		applications = new ArrayList<Application>(2);
	}
	
	public Exception getReadingException() {
		return (Exception)getProperty(SPEC.PROP.EXCEPTION);
	}
	
	public boolean hasReadingExcption() {
		return hasProperty(SPEC.PROP.EXCEPTION);
	}
	
	public boolean isUnknownCard() {
		return applicationCount() == 0;
	}
	
	public final int applicationCount() {
		return applications.size();
	}
	
	public final Application getApplication(int index) {
		return applications.get(index);
	}
	
	public final void addApplication(Application app) {
		if(app != null)
			applications.add(app);
	}
}
