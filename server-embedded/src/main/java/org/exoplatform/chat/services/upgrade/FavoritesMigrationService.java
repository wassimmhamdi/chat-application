/*
 * Copyright (C) 2018 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.chat.services.upgrade;

import com.mongodb.*;
import org.exoplatform.chat.listener.ConnectionManager;
import org.exoplatform.chat.services.ChatService;
import org.exoplatform.chat.services.SettingDataStorage;
import org.exoplatform.chat.services.mongodb.MongoBootstrap;
import org.exoplatform.chat.services.mongodb.UserMongoDataStorage;
import org.exoplatform.chat.utils.ChatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service to migrate users favorites to their new pattern:
 * * 1:1 rooms : username to roomId
 * * team rooms : team-{roomId} to roomId
 * * team rooms : space-{roomId} to roomId
 */
@Named("favoritesMigrationService")
@ApplicationScoped
@Singleton
public class FavoritesMigrationService {

  private static final Logger LOG = LoggerFactory.getLogger(FavoritesMigrationService.class);

  private static final String SETTING_MIGRATION_STATUS = "upgrade-favorites";

  @Inject
  private SettingDataStorage settingDataStorage;

  enum FavoritesMigrationStatus {
    RUNNING,
    DONE
  }

  public FavoritesMigrationService() {
  }

  public void processMigration() {
    MongoBootstrap mongoBootstrap = ConnectionManager.getInstance();
    DB db = mongoBootstrap.getDB();

    FavoritesMigrationStatus migrationStatus = getMigrationStatus(db.getName());

    if(migrationStatus == null) {
      setMigrationStatus(FavoritesMigrationStatus.RUNNING, db.getName());

      LOG.info("== Chat users favorites migration starting ==");

      DBCollection usersCol = db.getCollection(UserMongoDataStorage.M_USERS_COLLECTION);
      long totalNbOfUsers = usersCol.count();
      LOG.info("  Chat users favorites migration - Nb of users to migrate : " + totalNbOfUsers);
      DBCursor usersCursor = usersCol.find();

      int nbOfUsersProcessed = 0;
      while(usersCursor.hasNext()) {
        DBObject user = usersCursor.next();
        try {
          migrateUserFavorites(usersCol, user);
        } catch (Exception e) {
          LOG.error("Error while migrating Chat favorites of user " + user.get("user") + " : " + e.getMessage(), e);
        }
        nbOfUsersProcessed++;
        if(nbOfUsersProcessed%100 == 0 || nbOfUsersProcessed == totalNbOfUsers) {
          LOG.info("  Chat users favorites migration - Progress : " + nbOfUsersProcessed + "/" + totalNbOfUsers);
        }
      }

      setMigrationStatus(FavoritesMigrationStatus.DONE, db.getName());

      LOG.info("== Chat users favorites migration done ==");
    }
  }

  public FavoritesMigrationStatus getMigrationStatus(String dbName) {
    String status = settingDataStorage.getSetting(SETTING_MIGRATION_STATUS, dbName);
    if(status != null) {
      return FavoritesMigrationStatus.valueOf(status);
    }
    return null;
  }

  public void setMigrationStatus(FavoritesMigrationStatus status, String dbName) {
    settingDataStorage.setSetting(SETTING_MIGRATION_STATUS, status.toString(), dbName);
  }

  /**
   * Migrate favorites of the given user
   * @param usersCol Mongo users collection
   * @param user Mongo user object
   * @throws Exception
   */
  private void migrateUserFavorites(DBCollection usersCol, DBObject user) throws Exception {
    if (user != null) {
      List<String> favorites = (List<String>) user.get("favorites");

      if (favorites != null) {
        List<String> newFavorites = favorites.stream().filter(Objects::nonNull).map(oldFavorite -> {
          if (oldFavorite.startsWith(ChatService.TEAM_PREFIX)) {
            return oldFavorite.substring(ChatService.TEAM_PREFIX.length());
          } else if (oldFavorite.startsWith(ChatService.SPACE_PREFIX)) {
            return oldFavorite.substring(ChatService.SPACE_PREFIX.length());
          } else {
            return ChatUtils.getRoomId(Arrays.asList((String) user.get("user"), oldFavorite));
          }
        }).collect(Collectors.toList());

        BasicDBObject searchQuery = new BasicDBObject("_id", user.get("_id"));

        BasicDBObject updateQuery = new BasicDBObject();
        updateQuery.append("$set", new BasicDBObject().append("favorites", newFavorites));

        usersCol.update(searchQuery, updateQuery);
      }
    }
  }
}
