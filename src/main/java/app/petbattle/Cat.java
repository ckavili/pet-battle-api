package app.petbattle;

import app.petbattle.utils.Scalr;
import io.quarkus.mongodb.panache.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@MongoEntity(collection = "cats")
public class Cat extends PanacheMongoEntity {

    public Integer count;

    public Boolean vote;

    public Boolean issfw;

    public String image;

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Boolean getVote() {
        return vote;
    }

    public void setVote(Boolean vote) {
        this.vote = vote;
    }

    public Boolean getIssfw() { return issfw; }

    public void setIssfw(Boolean issfw) { this.issfw = issfw; }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public void vote() {
        if (vote) {
            setCount(getCount() + 1);
        } else {
            setCount(getCount() - 1);
        }
    }

    public void resizeCat() {
        try {
            String p = "^data:image/([^;]*);base64,?";
            String raw = getImage().replaceFirst(p, "");
            byte[] imageData = Base64.getDecoder().decode(raw);
            InputStream is = new ByteArrayInputStream(imageData);
            BufferedImage _tmp = ImageIO.read(is);
            BufferedImage scaledImage = Scalr.resize(_tmp, 300); // Scale image
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BufferedImage newImage = new BufferedImage( scaledImage.getWidth(), scaledImage.getHeight(), BufferedImage.TYPE_INT_RGB);
            newImage.createGraphics().drawImage( scaledImage, 0, 0, null);
            ImageIO.write(newImage, "jpeg", baos);
            baos.flush();
            String encodedString = Base64
                    .getEncoder()
                    .encodeToString(baos.toByteArray());
            setImage("data:image/jpeg;base64," + encodedString);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
