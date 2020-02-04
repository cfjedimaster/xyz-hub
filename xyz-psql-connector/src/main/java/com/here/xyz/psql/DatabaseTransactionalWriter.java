package com.here.xyz.psql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.here.xyz.models.geojson.implementation.Feature;
import com.here.xyz.models.geojson.implementation.FeatureCollection;
import com.here.xyz.models.geojson.implementation.Geometry;
import com.vividsolutions.jts.io.WKBWriter;
import org.postgresql.util.PGobject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

public class DatabaseTransactionalWriter extends  DatabaseWriter{

    public static FeatureCollection insertFeatures(FeatureCollection collection,
                                                   List<Feature> inserts, Connection connection, String schema, String table)
            throws SQLException, JsonProcessingException {

        String insertStmtSQL = "INSERT INTO ${schema}.${table} (jsondata, geo, geojson) VALUES(?::jsonb, ST_Force3D(ST_GeomFromWKB(?,4326)), ?::jsonb)";
        insertStmtSQL = SQLQuery.replaceVars(insertStmtSQL, schema, table);
        boolean batchInsert = false;

        String insertWithoutGeometryStmtSQL = "INSERT INTO ${schema}.${table} (jsondata, geo, geojson) VALUES(?::jsonb, NULL, NULL)";
        insertWithoutGeometryStmtSQL = SQLQuery.replaceVars(insertWithoutGeometryStmtSQL, schema, table);
        boolean batchInsertWithoutGeometry = false;

        final PreparedStatement insertStmt = createStatement(connection, insertStmtSQL);
        final PreparedStatement insertWithoutGeometryStmt = createStatement(connection, insertWithoutGeometryStmtSQL);

        for (int i = 0; i < inserts.size(); i++) {
            final Feature feature = inserts.get(i);
            final Geometry geometry = feature.getGeometry();
            feature.setGeometry(null); // Do not serialize the geometry in the JSON object

            final String json;
            final String geojson;
            try {
                json = feature.serialize();
                geojson = geometry != null ? geometry.serialize() : null;
            } finally {
                feature.setGeometry(geometry);
            }

            final PGobject jsonbObject = new PGobject();
            jsonbObject.setType("jsonb");
            jsonbObject.setValue(json);

            final PGobject geojsonbObject = new PGobject();
            geojsonbObject.setType("jsonb");
            geojsonbObject.setValue(geojson);

            if (geometry == null) {
                insertWithoutGeometryStmt.setObject(1, jsonbObject);
                insertWithoutGeometryStmt.addBatch();
                batchInsertWithoutGeometry = true;
            } else {
                insertStmt.setObject(1, jsonbObject);
                final WKBWriter wkbWriter = new WKBWriter(3);
                insertStmt.setBytes(2, wkbWriter.write(geometry.getJTSGeometry()));
                insertStmt.setObject(3, geojsonbObject);

                insertStmt.addBatch();
                batchInsert = true;
            }
            collection.getFeatures().add(feature);
        }

        if (batchInsert) {
            insertStmt.executeBatch();
        }
        if (batchInsertWithoutGeometry) {
            insertWithoutGeometryStmt.executeBatch();
        }

        return collection;
    }

    public static FeatureCollection updateFeatures(FeatureCollection collection,
                                                   List<Feature> updates, Connection connection, String schema, String table)
            throws SQLException, JsonProcessingException {

        String updateStmtSQL = "UPDATE ${schema}.${table} SET jsondata = ?::jsonb, geo=ST_Force3D(ST_GeomFromWKB(?,4326)), geojson = ?::jsonb WHERE jsondata->>'id' = ?";
        updateStmtSQL = SQLQuery.replaceVars(updateStmtSQL, schema, table);
        boolean batchUpdate = false;

        String updateWithoutGeometryStmtSQL = "UPDATE ${schema}.${table} SET  jsondata = ?::jsonb, geo=NULL, geojson = NULL WHERE jsondata->>'id' = ?";
        updateWithoutGeometryStmtSQL = SQLQuery.replaceVars(updateWithoutGeometryStmtSQL, schema, table);
        boolean batchUpdateWithoutGeometry = false;

        final PreparedStatement updateStmt = createStatement(connection, updateStmtSQL);
        final PreparedStatement updateWithoutGeometryStmt = createStatement(connection, updateWithoutGeometryStmtSQL);

        for (int i = 0; i < updates.size(); i++) {
            final Feature feature = updates.get(i);
            if (feature.getId() == null) {
                throw new NullPointerException("id");
            }
            final String id = feature.getId();
            final Geometry geometry = feature.getGeometry();
            feature.setGeometry(null); // Do not serialize the geometry in the JSON object

            final String json;
            final String geojson;

            try {
                json = feature.serialize();
                geojson = geometry != null ? geometry.serialize() : null;
            } finally {
                feature.setGeometry(geometry);
            }

            final PGobject jsonbObject = new PGobject();
            jsonbObject.setType("jsonb");
            jsonbObject.setValue(json);

            final PGobject geojsonbObject = new PGobject();
            geojsonbObject.setType("jsonb");
            geojsonbObject.setValue(geojson);

            if (geometry == null) {
                updateWithoutGeometryStmt.setObject(1, jsonbObject);
                updateWithoutGeometryStmt.setString(2, id);
                updateWithoutGeometryStmt.addBatch();
                batchUpdateWithoutGeometry = true;
            } else {
                updateStmt.setObject(1, jsonbObject);
                final WKBWriter wkbWriter = new WKBWriter(3);
                updateStmt.setBytes(2, wkbWriter.write(geometry.getJTSGeometry()));
                updateStmt.setObject(3, geojsonbObject);
                updateStmt.setString(4, id);
                updateStmt.addBatch();
                batchUpdate = true;
            }
            collection.getFeatures().add(feature);
        }

        if (batchUpdate) {
            updateStmt.executeBatch();
        }
        if (batchUpdateWithoutGeometry) {
            updateWithoutGeometryStmt.executeBatch();
        }
        return collection;
    }
}