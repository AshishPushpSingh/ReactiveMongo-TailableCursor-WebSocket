package controllers;

/**
 * Created by Ashish Pushp Singh on 7/8/17.
 */
public class Item {

    private String id;
    private boolean status;
    private String batchId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String toString(){
        return "id : " + id + ", status : " + status + ", batchId : " + batchId;
    }
}
