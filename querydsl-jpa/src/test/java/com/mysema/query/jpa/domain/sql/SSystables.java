package com.mysema.query.jpa.domain.sql;

import static com.mysema.query.types.PathMetadataFactory.*;

import com.mysema.query.types.*;
import com.mysema.query.types.path.*;

import javax.annotation.Generated;


/**
 * SSystables is a Querydsl query type for SSystables
 */
@Generated("com.mysema.query.sql.MetaDataSerializer")
public class SSystables extends com.mysema.query.sql.RelationalPathBase<SSystables> {

    private static final long serialVersionUID = -1051208287;

    public static final SSystables systables = new SSystables("SYSTABLES");

    public final StringPath lockgranularity = createString("LOCKGRANULARITY");

    public final StringPath schemaid = createString("SCHEMAID");

    public final StringPath tableid = createString("TABLEID");

    public final StringPath tablename = createString("TABLENAME");

    public final StringPath tabletype = createString("TABLETYPE");

    public SSystables(String variable) {
        super(SSystables.class, forVariable(variable), "SYS", "SYSTABLES");
    }

    public SSystables(Path<? extends SSystables> entity) {
        super(entity.getType(), entity.getMetadata(), "SYS", "SYSTABLES");
    }

    public SSystables(PathMetadata<?> metadata) {
        super(SSystables.class, metadata, "SYS", "SYSTABLES");
    }

}

