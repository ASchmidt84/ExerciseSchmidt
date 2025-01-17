package backend.service

import akka.actor.typed.ActorSystem
import backend.proto
import backend.proto.BackendService

/**
 * Das ist die implementierte Schnittstelle
 * Es gibt jedoch keine Calls die von extern nach hier benutzt werden.
 * @param system
 */
class BackendServiceImpl(private val system: ActorSystem[_]) extends BackendService {

}
