# Generated stub for silvertongue.dictionary proto — gRPC service
# Manual implementation matching dictionary.proto

import grpc
import dictionary_pb2


GRPC_GENERATED_VERSION = '1.66.0'
GRPC_VERSION = grpc.__version__


class DictionaryServiceStub:
    """Client stub for DictionaryService."""

    def __init__(self, channel):
        self.LookupWord = channel.unary_unary(
            '/silvertongue.dictionary.DictionaryService/LookupWord',
            request_serializer=dictionary_pb2.LookupRequest.SerializeToString,
            response_deserializer=dictionary_pb2.LookupResponse.FromString,
            _registered_method=True,
        )


class DictionaryServiceServicer:
    """Server servicer for DictionaryService."""

    def LookupWord(self, request, context):
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')


def add_DictionaryServiceServicer_to_server(servicer, server):
    rpc_method_handlers = {
        'LookupWord': grpc.unary_unary_rpc_method_handler(
            servicer.LookupWord,
            request_deserializer=dictionary_pb2.LookupRequest.FromString,
            response_serializer=dictionary_pb2.LookupResponse.SerializeToString,
        ),
    }
    generic_handler = grpc.method_handlers_generic_handler(
        'silvertongue.dictionary.DictionaryService', rpc_method_handlers)
    server.add_generic_rpc_handlers((generic_handler,))
    server.add_registered_method_handlers(
        'silvertongue.dictionary.DictionaryService', rpc_method_handlers)
