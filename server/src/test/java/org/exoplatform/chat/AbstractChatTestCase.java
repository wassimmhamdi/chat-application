package org.exoplatform.chat;

import com.google.inject.AbstractModule;
import org.cometd.bayeux.server.BayeuxServer;
import org.exoplatform.chat.bootstrap.ServiceBootstrap;
import org.exoplatform.chat.listener.ConnectionManager;
import org.exoplatform.chat.listener.GuiceManager;
import org.exoplatform.chat.model.RealTimeMessageBean;
import org.exoplatform.chat.services.*;
import org.exoplatform.chat.services.mongodb.*;
import org.exoplatform.chat.utils.PropertyManager;
import org.exoplatform.services.user.UserStateService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class AbstractChatTestCase
{
  static Logger log = Logger.getLogger("ChatTestCase");

  @BeforeClass
  public static void before() throws IOException
  {
    PropertyManager.overrideProperty(PropertyManager.PROPERTY_SERVER_TYPE, "embed");
    PropertyManager.overrideProperty(PropertyManager.PROPERTY_SERVER_PORT, "27777");
    PropertyManager.overrideProperty(PropertyManager.PROPERTY_TOKEN_VALIDITY, "100");

    ConnectionManager.forceNew();
    ConnectionManager.getInstance().getDB("unittest");

    GuiceManager.forceNew(new TestModule());
    ServiceBootstrap.forceNew();
  }

  @AfterClass
  public static void teardown() throws Exception {
    ConnectionManager.getInstance().close();
  }

  /**
   * Guice module allowing to mock services for tests
   */
  private static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      UserStateService mockUserStateService = Mockito.mock(UserStateService.class);
      MockitoAnnotations.initMocks(mockUserStateService);

      bind(ChatDataStorage.class).to(ChatMongoDataStorage.class);
      bind(ChatService.class).to(ChatServiceImpl.class);
      bind(NotificationDataStorage.class).to(NotificationMongoDataStorage.class);
      bind(NotificationService.class).to(NotificationServiceImpl.class);
      bind(TokenStorage.class).to(TokenMongoService.class);
      bind(TokenService.class).toInstance(new TokenServiceImpl(mockUserStateService));
      bind(UserDataStorage.class).to(UserMongoDataStorage.class);
      bind(UserService.class).to(UserServiceImpl.class);
      // mock for RealTimeMessageService
      bind(RealTimeMessageService.class).toInstance(new RealTimeMessageService() {
        @Override
        public void setBayeux(BayeuxServer bayeux) {

        }

        @Override
        public void sendMessage(RealTimeMessageBean realTimeMessageBean, String receiver) {
        }

        @Override
        public void sendMessage(RealTimeMessageBean realTimeMessageBean, List<String> receivers) {
        }

        @Override
        public void sendMessageToAll(RealTimeMessageBean realTimeMessageBean) {
        }
      });
    }
  }
}