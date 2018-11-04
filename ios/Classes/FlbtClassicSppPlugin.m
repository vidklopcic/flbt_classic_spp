#import "FlbtClassicSppPlugin.h"
#import <flbt_classic_spp/flbt_classic_spp-Swift.h>

@implementation FlbtClassicSppPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlbtClassicSppPlugin registerWithRegistrar:registrar];
}
@end
