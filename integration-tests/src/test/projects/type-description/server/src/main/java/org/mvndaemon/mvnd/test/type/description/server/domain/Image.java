/*
 * Copyright 2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mvndaemon.mvnd.test.type.description.server.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.sql.rowset.serial.SerialBlob;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;

/**
 * @author Ales Justin
 */
@Entity
@Table(
        name = Image.TABLE_NAME,
        uniqueConstraints = @UniqueConstraint(name = Image.PK_CONSTRAINT_NAME, columnNames = {"name"})
)
public class Image extends AbstractEntity {
    public static final String TABLE_NAME = "image";
    public static final String PK_CONSTRAINT_NAME = TABLE_NAME + "_pkey";

    @Column(nullable = false)
    private String name;
    @Lob
    private Blob blob;
    private String mimeType;
    private long length;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public Blob getBlob() {
        return blob;
    }

    public void setBlob(Blob blob) {
        this.blob = blob;
    }

    public void write(byte[] bytes) throws Exception {
        blob = new SerialBlob(bytes);
    }

    public byte[] read() throws IOException {
        if (blob == null)
            return null;

        try {
            return blob.getBytes(1, (int) blob.length() + 1);
        } catch (SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public InputStream stream() throws IOException {
        try {
            return (blob != null) ? blob.getBinaryStream() : null;
        } catch (SQLException e) {
            throw new IOException(e.getMessage(), e);
        }
    }
}
