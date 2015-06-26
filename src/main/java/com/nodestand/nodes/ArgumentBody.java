package com.nodestand.nodes;

import com.nodestand.nodes.comment.Commentable;
import com.nodestand.nodes.version.Build;
import com.nodestand.nodes.version.MajorVersion;
import com.nodestand.nodes.version.VersionHelper;
import jdk.nashorn.internal.runtime.Version;
import org.neo4j.graphdb.Direction;
import org.springframework.data.neo4j.annotation.*;
import org.springframework.data.neo4j.support.index.IndexType;

@NodeEntity
public abstract class ArgumentBody implements Commentable {

    @GraphId
    protected Long id;

    @Indexed(indexName = "body-search", indexType= IndexType.FULLTEXT)
    private String title;

    @Fetch
    @RelatedTo(type="AUTHORED_BY", direction = Direction.OUTGOING)
    public User author;

    @Fetch
    @RelatedTo(type="VERSION_OF", direction = Direction.OUTGOING)
    private MajorVersion majorVersion;

    private int minorVersion;

    public ArgumentBody() {}

    public ArgumentBody(String title, User author) {
        this.title = title;
        this.author = author;
    }

    public long getId() {
        return id;
    }

    public void setVersion(MajorVersion majorVersion, int minorVersion) {
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }

    public String getTitle() {
        return title;
    }

    public MajorVersion getMajorVersion() {
        return majorVersion;
    }

    public int getMinorVersion() {
        return minorVersion;
    }

    public abstract ArgumentNode constructNode(VersionHelper versionHelper);

    public void setMinorVersion(int minorVersion) {
        this.minorVersion = minorVersion;
    }

    public String getVersionString() {
        return majorVersion.getVersionNumber() + "." + minorVersion;
    }
}
